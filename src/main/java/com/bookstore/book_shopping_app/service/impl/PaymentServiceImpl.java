
package com.bookstore.book_shopping_app.service.impl;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.entity.PaymentAttempt;
import com.bookstore.book_shopping_app.entity.ProcessedPaymentEvent;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.enums.PaymentEventType;
import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import com.bookstore.book_shopping_app.exception.business.CommonException;
import com.bookstore.book_shopping_app.payment.MockPaymentEvent;
import com.bookstore.book_shopping_app.payment.gateway.GatewayPaymentRequest;
import com.bookstore.book_shopping_app.payment.gateway.PaymentGateway;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.repository.PaymentAttemptRepository;
import com.bookstore.book_shopping_app.repository.ProcessedPaymentEventRepository;
import com.bookstore.book_shopping_app.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final String PAYMENT_SUCCEEDED_EVENT =
            "payment_intent.succeeded";

    private static final String PAYMENT_FAILED_EVENT =
            "payment_intent.payment_failed";

    private static final String HASH_ALGORITHM = "SHA-256";

    private static final String PAYLOAD_SEPARATOR = "|";

    private final PaymentAttemptRepository paymentAttempts;
    private final ProcessedPaymentEventRepository processedEvents;
    private final OrderRepository orders;
    private final BookRepository books;
    private final Clock clock;
    private final List<PaymentGateway> gateways;
    private final CartPurchaseCleanupService cartPurchaseCleanupService;

    @Override
    @Transactional
    public void processEvent(MockPaymentEvent event) {

        PaymentEventType eventType = resolveEventType(event.type());
        String payloadHash = calculatePayloadHash(event);

        var existingEvent = processedEvents.findByEventId(event.eventId());

        if (existingEvent.isPresent()) {
            validateDuplicateEvent(
                    existingEvent.get(), event, eventType, payloadHash
            );
            return;
        }

        PaymentAttempt attempt = paymentAttempts
                .findLockedByPaymentIntentId(event.paymentIntentId())
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.PAYMENT_ATTEMPT_NOT_FOUND,
                        BookstoreMessageConstants.PAYMENT_ATTEMPT_NOT_FOUND_MESSAGE
                ));

        Order order = orders.findLockedById(attempt.getOrder().getId())
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.ORDER_NOT_FOUND,
                        BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE
                ));

        validatePaymentDetails(attempt, event);

        if (eventType == PaymentEventType.PAYMENT_SUCCEEDED) {
            processSuccessfulPayment(attempt, order, event);
        } else {
            processFailedPayment(attempt, order, event);
        }

        processedEvents.save(
                new ProcessedPaymentEvent(
                        event.eventId(),
                        eventType,
                        event.paymentIntentId(),
                        payloadHash
                )
        );
    }

    private void processSuccessfulPayment(
            PaymentAttempt attempt,
            Order order,
            MockPaymentEvent event) {

        LocalDateTime now = LocalDateTime.now(clock);

        // Handle a success webhook received after order expiry.
        if (order.getStatus() == OrderStatus.EXPIRED
                || (order.getStatus() == OrderStatus.PENDING
                && !order.getExpiresAt().isAfter(now))) {

            if (order.getStatus() == OrderStatus.PENDING) {
                expirePendingOrder(order);
            }

            attempt.markSucceeded(event.gatewayReference());
            order.updateStatus(OrderStatus.PAID);

            cartPurchaseCleanupService.removePurchasedItems(order);

            log.warn(
                    "Payment succeeded after order expiry. Order ID: {}, payment intent: {}",
                    order.getId(),
                    attempt.getPaymentIntentId()
            );

            return;
        }

        if (order.getStatus() == OrderStatus.PAID) {
            attempt.markSucceeded(event.gatewayReference());
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CommonException(
                    BookstoreErrorConstants.INVALID_ORDER_STATUS,
                    String.format(
                            BookstoreMessageConstants.INVALID_ORDER_STATUS_MESSAGE,
                            order.getStatus()
                    )
            );
        }

        attempt.markSucceeded(event.gatewayReference());
        order.updateStatus(OrderStatus.PAID);

        // Remove purchased quantities only after successful payment.
        cartPurchaseCleanupService.removePurchasedItems(order);
    }

    private void processFailedPayment(
            PaymentAttempt attempt,
            Order order,
            MockPaymentEvent event) {

        // A late failure must not change an already completed order.
        if (order.getStatus() == OrderStatus.EXPIRED
                || order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.REQUIRES_RECONCILIATION) {
            return;
        }

        if (attempt.getStatus() == PaymentStatus.FAILED
                || attempt.getStatus() == PaymentStatus.EXPIRED) {
            return;
        }

        attempt.markFailed(event.failureReason());

        // Keep the order pending so the customer can retry payment.
    }

    @Override
    @Transactional
    public void expireOrder(Long orderId) {

        Order order = orders.findLockedById(orderId)
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.ORDER_NOT_FOUND,
                        BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE
                ));

        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);

        if (order.getExpiresAt() == null
                || order.getExpiresAt().isAfter(now)) {
            return;
        }

        expirePendingOrder(order);
    }

    private void expirePendingOrder(Order order) {

        // Restore reserved stock exactly once.
        order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getBook().getId()))
                .forEach(item -> {

                    var book = books.findLockedById(item.getBook().getId())
                            .orElseThrow(() -> new CommonException(
                                    BookstoreErrorConstants.BOOK_NOT_FOUND,
                                    BookstoreMessageConstants.BOOK_NOT_FOUND_WHILE_RESTORING_STOCK_MESSAGE
                            ));

                    int restoredStock = Math.addExact(
                            book.getStockQuantity(),
                            item.getQuantity()
                    );

                    book.updateStock(restoredStock);
                });

        // Expire all active payment attempts belonging to this order.
        paymentAttempts.findByOrder_IdOrderByCreatedAtDesc(order.getId())
                .forEach(attempt -> {
                    if (attempt.getStatus() == PaymentStatus.INITIATED
                            || attempt.getStatus() == PaymentStatus.PENDING) {
                        attempt.markExpired();
                    }
                });

        order.updateStatus(OrderStatus.EXPIRED);
    }

    private void validatePaymentDetails(
            PaymentAttempt attempt,
            MockPaymentEvent event) {

        BigDecimal eventAmount = BigDecimal.valueOf(event.amountMinor(), 2);

        if (attempt.getAmount().compareTo(eventAmount) != 0) {
            throw new CommonException(
                    BookstoreErrorConstants.PAYMENT_AMOUNT_MISMATCH,
                    BookstoreMessageConstants.PAYMENT_AMOUNT_MISMATCH_MESSAGE
            );
        }

        if (!attempt.getCurrency().equalsIgnoreCase(event.currency())) {
            throw new CommonException(
                    BookstoreErrorConstants.PAYMENT_CURRENCY_MISMATCH,
                    BookstoreMessageConstants.PAYMENT_CURRENCY_MISMATCH_MESSAGE
            );
        }
    }

    private PaymentEventType resolveEventType(String type) {

        return switch (type) {
            case PAYMENT_SUCCEEDED_EVENT ->
                    PaymentEventType.PAYMENT_SUCCEEDED;

            case PAYMENT_FAILED_EVENT ->
                    PaymentEventType.PAYMENT_FAILED;

            default -> throw new CommonException(
                    BookstoreErrorConstants.UNSUPPORTED_PAYMENT_EVENT,
                    BookstoreMessageConstants.UNSUPPORTED_PAYMENT_EVENT_MESSAGE
            );
        };
    }

    private void validateDuplicateEvent(
            ProcessedPaymentEvent existing,
            MockPaymentEvent event,
            PaymentEventType eventType,
            String payloadHash) {

        boolean sameEvent =
                existing.getEventType() == eventType
                        && existing.getPaymentIntentId().equals(event.paymentIntentId())
                        && existing.getPayloadHash().equals(payloadHash);

        if (!sameEvent) {
            throw new CommonException(
                    BookstoreErrorConstants.EVENT_ID_REUSED,
                    BookstoreMessageConstants.EVENT_ID_REUSED_MESSAGE
            );
        }
    }

    private String calculatePayloadHash(MockPaymentEvent event) {

        String canonicalPayload = String.join(
                PAYLOAD_SEPARATOR,
                event.eventId(),
                event.type(),
                event.paymentIntentId(),
                event.amountMinor().toString(),
                event.currency().toUpperCase(),
                event.gatewayReference() == null ? "" : event.gatewayReference(),
                event.failureReason() == null ? "" : event.failureReason()
        );

        try {
            byte[] hash = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }

    @Override
    @Transactional
    public PaymentResult initiatePayment(
            String email,
            Long orderId,
            PaymentMethod provider) {

        Order order = orders.findLockedById(orderId)
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.ORDER_NOT_FOUND,
                        BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE
                ));

        // A customer can initiate payment only for their own order.
        if (!order.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new CommonException(
                    BookstoreErrorConstants.ORDER_NOT_FOUND,
                    BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE
            );
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CommonException(
                    BookstoreErrorConstants.INVALID_ORDER_STATUS,
                    BookstoreMessageConstants.PAYMENT_ONLY_FOR_PENDING_ORDER_MESSAGE
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);

        if (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(now)) {
            throw new CommonException(
                    BookstoreErrorConstants.ORDER_EXPIRED,
                    BookstoreMessageConstants.ORDER_EXPIRED_MESSAGE
            );
        }

        // Avoid creating another active payment attempt for the same order.
        List<PaymentAttempt> attempts =
                paymentAttempts.findByOrder_IdOrderByCreatedAtDesc(orderId);

        for (PaymentAttempt attempt : attempts) {
            if (attempt.getStatus() == PaymentStatus.INITIATED
                    || attempt.getStatus() == PaymentStatus.PENDING) {

                if (attempt.getProvider() != provider) {
                    throw new CommonException(
                            BookstoreErrorConstants.PAYMENT_ALREADY_INITIATED,
                            BookstoreMessageConstants.PAYMENT_ALREADY_INITIATED_MESSAGE
                    );
                }

                // Repeated initiation with the same provider returns the existing attempt.
                return new PaymentResult(
                        attempt.getPaymentIntentId(),
                        attempt.getClientToken(),
                        attempt.getProvider(),
                        attempt.getStatus()
                );
            }
        }

        PaymentGateway gateway = gateways.stream()
                .filter(candidate -> candidate.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.PAYMENT_PROVIDER_NOT_SUPPORTED,
                        BookstoreMessageConstants.PAYMENT_PROVIDER_NOT_SUPPORTED_MESSAGE
                ));

        // Always use the amount and currency stored in the order.
        PaymentResult result = gateway.initiatePayment(
                new GatewayPaymentRequest(
                        order.getTotalAmount(),
                        order.getCurrency()
                )
        );

        // Validate the gateway response.
        if (result == null || result.provider() != provider) {
            throw new CommonException(
                    BookstoreErrorConstants.INVALID_PAYMENT_GATEWAY_RESPONSE,
                    BookstoreMessageConstants.INVALID_PAYMENT_GATEWAY_RESPONSE_MESSAGE
            );
        }

        // Create payment attempt only after validating the response.
        PaymentAttempt attempt = new PaymentAttempt(
                order,
                provider,
                order.getTotalAmount(),
                order.getCurrency(),
                result.paymentIntentId(),
                result.clientToken(),
                order.getExpiresAt()
        );

        if (result.status() == PaymentStatus.PENDING) {
            attempt.markPending();
        } else if (result.status() != PaymentStatus.INITIATED) {
            throw new CommonException(
                    BookstoreErrorConstants.INVALID_PAYMENT_GATEWAY_RESPONSE,
                    BookstoreMessageConstants.INVALID_PAYMENT_GATEWAY_RESPONSE_MESSAGE
            );
        }

        paymentAttempts.save(attempt);

        return result;
    }
}
