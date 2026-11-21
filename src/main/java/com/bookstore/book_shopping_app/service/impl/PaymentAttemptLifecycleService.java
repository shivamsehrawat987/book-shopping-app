package com.bookstore.book_shopping_app.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;

import com.bookstore.book_shopping_app.entity.PaymentAttempt;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Centralizes valid payment-attempt state transitions. */
@Component
@RequiredArgsConstructor
public class PaymentAttemptLifecycleService {

    private final Clock clock;

    public void markSucceeded(PaymentAttempt attempt, String gatewayReference) {
        if (gatewayReference == null || gatewayReference.isBlank()) {
            throw new IllegalArgumentException("Gateway reference is required for successful payment");
        }
        if (attempt.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }
        requireStatus(attempt, PaymentStatus.PENDING, PaymentStatus.INITIATED, PaymentStatus.EXPIRED);
        attempt.setGatewayReference(gatewayReference);
        attempt.setFailureReason(null);
        attempt.setStatus(PaymentStatus.SUCCEEDED);
        touch(attempt);
    }

    public void markFailed(PaymentAttempt attempt, String reason) {
        if (attempt.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        requireStatus(attempt, PaymentStatus.PENDING, PaymentStatus.INITIATED);
        String normalized = reason == null || reason.isBlank()
                ? "Payment failed"
                : reason.length() > 500 ? reason.substring(0, 500) : reason;
        attempt.setFailureReason(normalized);
        attempt.setStatus(PaymentStatus.FAILED);
        touch(attempt);
    }

    public void markExpired(PaymentAttempt attempt) {
        if (attempt.getStatus() == PaymentStatus.EXPIRED) {
            return;
        }
        requireStatus(attempt, PaymentStatus.PENDING, PaymentStatus.INITIATED);
        attempt.setStatus(PaymentStatus.EXPIRED);
        touch(attempt);
    }

    private void requireStatus(PaymentAttempt attempt, PaymentStatus... allowed) {
        for (PaymentStatus status : allowed) {
            if (attempt.getStatus() == status) {
                return;
            }
        }
        throw new IllegalStateException(
                "Payment attempt cannot transition from status: " + attempt.getStatus());
    }

    private void touch(PaymentAttempt attempt) {
        attempt.setUpdatedAt(LocalDateTime.now(clock));
    }
}
