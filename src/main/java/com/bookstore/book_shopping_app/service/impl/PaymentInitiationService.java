package com.bookstore.book_shopping_app.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.entity.PaymentAttempt;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import com.bookstore.book_shopping_app.exception.business.CommonException;
import com.bookstore.book_shopping_app.payment.gateway.GatewayPaymentRequest;
import com.bookstore.book_shopping_app.payment.gateway.PaymentGateway;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentInitiationService {

    private final PaymentAttemptRepository paymentAttempts;
    private final OrderRepository orders;
    private final Clock clock;
    private final List<PaymentGateway> gateways;

    @Transactional
    public PaymentResult initiatePayment(String email, Long orderId, PaymentMethod provider) {
        Order order = orders.findLockedById(orderId)
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.ORDER_NOT_FOUND,
                        BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE));

        if (!order.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new CommonException(BookstoreErrorConstants.ORDER_NOT_FOUND,
                    BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CommonException(BookstoreErrorConstants.INVALID_ORDER_STATUS,
                    BookstoreMessageConstants.PAYMENT_ONLY_FOR_PENDING_ORDER_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(now)) {
            throw new CommonException(BookstoreErrorConstants.ORDER_EXPIRED,
                    BookstoreMessageConstants.ORDER_EXPIRED_MESSAGE);
        }

        List<PaymentAttempt> attempts =
                paymentAttempts.findByOrder_IdOrderByCreatedAtDesc(orderId);
        for (PaymentAttempt attempt : attempts) {
            if (attempt.getStatus() == PaymentStatus.INITIATED
                    || attempt.getStatus() == PaymentStatus.PENDING) {
                if (attempt.getProvider() != provider) {
                    throw new CommonException(BookstoreErrorConstants.PAYMENT_ALREADY_INITIATED,
                            BookstoreMessageConstants.PAYMENT_ALREADY_INITIATED_MESSAGE);
                }
                log.info("Reusing existing payment attempt: orderId={}, provider={}, status={}",
                        orderId, attempt.getProvider(), attempt.getStatus());
                return new PaymentResult(attempt.getPaymentIntentId(), attempt.getClientToken(),
                        attempt.getProvider(), attempt.getStatus());
            }
        }

        PaymentGateway gateway = gateways.stream()
                .filter(candidate -> candidate.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.PAYMENT_PROVIDER_NOT_SUPPORTED,
                        BookstoreMessageConstants.PAYMENT_PROVIDER_NOT_SUPPORTED_MESSAGE));

        // Note: the gateway call is still synchronous; see the follow-up note in the handoff.
        PaymentResult result = gateway.initiatePayment(
                new GatewayPaymentRequest(order.getTotalAmount(), order.getCurrency()));

        if (result == null || result.provider() != provider
                || (result.status() != PaymentStatus.PENDING
                && result.status() != PaymentStatus.INITIATED)) {
            throw new CommonException(BookstoreErrorConstants.INVALID_PAYMENT_GATEWAY_RESPONSE,
                    BookstoreMessageConstants.INVALID_PAYMENT_GATEWAY_RESPONSE_MESSAGE);
        }
        validateAttemptData(order, result);

        LocalDateTime createdAt = LocalDateTime.now(clock);
        PaymentAttempt attempt = PaymentAttempt.builder()
                .order(order)
                .provider(provider)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency().trim().toUpperCase(Locale.ROOT))
                .paymentIntentId(result.paymentIntentId())
                .clientToken(result.clientToken())
                .expiresAt(order.getExpiresAt())
                .status(result.status())
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        paymentAttempts.save(attempt);
        log.info("Payment initiated: orderId={}, provider={}, status={}",
                orderId, provider, result.status());
        return result;
    }

    private void validateAttemptData(Order order, PaymentResult result) {
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0
                || order.getCurrency() == null || order.getCurrency().isBlank()
                || result.paymentIntentId() == null || result.paymentIntentId().isBlank()
                || result.paymentIntentId().length() > 100
                || result.clientToken() == null || result.clientToken().isBlank()
                || result.clientToken().length() > 200) {
            throw new CommonException(BookstoreErrorConstants.INVALID_PAYMENT_GATEWAY_RESPONSE,
                    BookstoreMessageConstants.INVALID_PAYMENT_GATEWAY_RESPONSE_MESSAGE);
        }
    }
}
