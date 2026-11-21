package com.bookstore.book_shopping_app.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.entity.PaymentAttempt;
import com.bookstore.book_shopping_app.entity.ProcessedPaymentEvent;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.enums.PaymentEventType;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import com.bookstore.book_shopping_app.exception.business.CommonException;
import com.bookstore.book_shopping_app.payment.MockPaymentEvent;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.repository.PaymentAttemptRepository;
import com.bookstore.book_shopping_app.repository.ProcessedPaymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    private static final String PAYMENT_SUCCEEDED_EVENT = "payment_intent.succeeded";
    private static final String PAYMENT_FAILED_EVENT = "payment_intent.payment_failed";
    private static final String PAYLOAD_SEPARATOR = "|";

    private final PaymentAttemptRepository paymentAttempts;
    private final ProcessedPaymentEventRepository processedEvents;
    private final OrderRepository orders;
    private final PaymentAttemptLifecycleService paymentAttemptLifecycle;
    private final OrderExpirationService orderExpirationService;
    private final CartPurchaseCleanupService cartPurchaseCleanupService;
    private final Clock clock;

    @Transactional
    public void processEvent(MockPaymentEvent event) {
        PaymentEventType eventType = resolveEventType(event.type());
        String payloadHash = calculatePayloadHash(event);

        var existing = processedEvents.findByEventId(event.eventId());
        if (existing.isPresent()) {
            validateDuplicateEvent(existing.get(), event, eventType, payloadHash);
            log.info("Duplicate payment webhook ignored: eventId={}, eventType={}",
                    event.eventId(), eventType);
            return;
        }

        // Lock order first, then payment attempt. Expiry follows the same lock order.
        PaymentAttempt reference = paymentAttempts.findByPaymentIntentId(event.paymentIntentId())
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.PAYMENT_ATTEMPT_NOT_FOUND,
                        BookstoreMessageConstants.PAYMENT_ATTEMPT_NOT_FOUND_MESSAGE));
        Order order = orders.findLockedById(reference.getOrder().getId())
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.ORDER_NOT_FOUND,
                        BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE));
        PaymentAttempt attempt = paymentAttempts.findLockedByPaymentIntentId(event.paymentIntentId())
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.PAYMENT_ATTEMPT_NOT_FOUND,
                        BookstoreMessageConstants.PAYMENT_ATTEMPT_NOT_FOUND_MESSAGE));

        // A concurrent delivery for the same intent may have completed while this request waited.
        existing = processedEvents.findByEventId(event.eventId());
        if (existing.isPresent()) {
            validateDuplicateEvent(existing.get(), event, eventType, payloadHash);
            return;
        }

        validatePaymentDetails(attempt, event);
        if (eventType == PaymentEventType.PAYMENT_SUCCEEDED) {
            processSuccessfulPayment(attempt, order, event);
        } else {
            processFailedPayment(attempt, order, event);
        }

        processedEvents.save(ProcessedPaymentEvent.builder()
                .eventId(event.eventId())
                .eventType(eventType)
                .paymentIntentId(event.paymentIntentId())
                .payloadHash(payloadHash)
                .processedAt(LocalDateTime.now(clock))
                .build());
        log.info("Payment webhook processed: eventId={}, eventType={}, orderId={}, paymentStatus={}",
                event.eventId(), eventType, order.getId(), attempt.getStatus());
    }

    private void processSuccessfulPayment(PaymentAttempt attempt, Order order, MockPaymentEvent event) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean expired = order.getStatus() == OrderStatus.EXPIRED
                || (order.getStatus() == OrderStatus.PENDING
                && order.getExpiresAt() != null && !order.getExpiresAt().isAfter(now));

        if (expired) {
            if (order.getStatus() == OrderStatus.PENDING) {
                orderExpirationService.expirePendingOrder(order);
            }

            // The provider captured money after our stock reservation ended.
            // Do NOT mark the order PAID or clear the cart: stock is no longer reserved.
            paymentAttemptLifecycle.markSucceeded(attempt, event.gatewayReference());
            order.requireReconciliation();
            log.error("Payment captured after order expiry; refund/manual reconciliation required. "
                            + "Order ID: {}, payment intent: {}",
                    order.getId(), attempt.getPaymentIntentId());
            return;
        }

        if (order.getStatus() == OrderStatus.PAID) {
            paymentAttemptLifecycle.markSucceeded(attempt, event.gatewayReference());
            return;
        }
        if (order.getStatus() == OrderStatus.REQUIRES_RECONCILIATION) {
            paymentAttemptLifecycle.markSucceeded(attempt, event.gatewayReference());
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CommonException(BookstoreErrorConstants.INVALID_ORDER_STATUS,
                    String.format(BookstoreMessageConstants.INVALID_ORDER_STATUS_MESSAGE,
                            order.getStatus()));
        }

        paymentAttemptLifecycle.markSucceeded(attempt, event.gatewayReference());
        order.markPaid();
        log.info("Order payment confirmed: orderId={}, paymentIntentId={}",
                order.getId(), attempt.getPaymentIntentId());
        cartPurchaseCleanupService.removePurchasedItems(order);
    }

    private void processFailedPayment(PaymentAttempt attempt, Order order, MockPaymentEvent event) {
        if (order.getStatus() == OrderStatus.EXPIRED
                || order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.REQUIRES_RECONCILIATION) {
            return;
        }
        if (attempt.getStatus() == PaymentStatus.FAILED
                || attempt.getStatus() == PaymentStatus.EXPIRED) {
            return;
        }
        paymentAttemptLifecycle.markFailed(attempt, event.failureReason());
        log.warn("Payment attempt failed: orderId={}, paymentIntentId={}",
                order.getId(), attempt.getPaymentIntentId());
    }

    private void validatePaymentDetails(PaymentAttempt attempt, MockPaymentEvent event) {
        BigDecimal eventAmount = BigDecimal.valueOf(event.amountMinor(), 2);
        if (attempt.getAmount().compareTo(eventAmount) != 0) {
            throw new CommonException(BookstoreErrorConstants.PAYMENT_AMOUNT_MISMATCH,
                    BookstoreMessageConstants.PAYMENT_AMOUNT_MISMATCH_MESSAGE);
        }
        if (!attempt.getCurrency().equalsIgnoreCase(event.currency())) {
            throw new CommonException(BookstoreErrorConstants.PAYMENT_CURRENCY_MISMATCH,
                    BookstoreMessageConstants.PAYMENT_CURRENCY_MISMATCH_MESSAGE);
        }
    }

    private PaymentEventType resolveEventType(String type) {
        return switch (type) {
            case PAYMENT_SUCCEEDED_EVENT -> PaymentEventType.PAYMENT_SUCCEEDED;
            case PAYMENT_FAILED_EVENT -> PaymentEventType.PAYMENT_FAILED;
            default -> throw new CommonException(BookstoreErrorConstants.UNSUPPORTED_PAYMENT_EVENT,
                    BookstoreMessageConstants.UNSUPPORTED_PAYMENT_EVENT_MESSAGE);
        };
    }

    private void validateDuplicateEvent(ProcessedPaymentEvent existing, MockPaymentEvent event,
                                        PaymentEventType eventType, String payloadHash) {
        boolean same = existing.getEventType() == eventType
                && existing.getPaymentIntentId().equals(event.paymentIntentId())
                && existing.getPayloadHash().equals(payloadHash);
        if (!same) {
            throw new CommonException(BookstoreErrorConstants.EVENT_ID_REUSED,
                    BookstoreMessageConstants.EVENT_ID_REUSED_MESSAGE);
        }
    }

    private String calculatePayloadHash(MockPaymentEvent event) {
        String canonical = String.join(PAYLOAD_SEPARATOR,
                event.eventId(), event.type(), event.paymentIntentId(),
                event.amountMinor().toString(), event.currency().toUpperCase(Locale.ROOT),
                event.gatewayReference() == null ? "" : event.gatewayReference(),
                event.failureReason() == null ? "" : event.failureReason());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
