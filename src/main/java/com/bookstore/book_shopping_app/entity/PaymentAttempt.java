package com.bookstore.book_shopping_app.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_attempt_intent", columnNames = "payment_intent_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod provider;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Column(nullable = false, length = 3)
    private String currency;

    @NotBlank
    @Column(name = "payment_intent_id", nullable = false, length = 100)
    private String paymentIntentId;

    @NotBlank
    @Column(name = "client_token", nullable = false, length = 200)
    private String clientToken;

    @Column(name = "gateway_reference", length = 150)
    private String gatewayReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PaymentAttempt(Order order, PaymentMethod provider, BigDecimal amount,
                          String currency, String paymentIntentId, String clientToken,
                          LocalDateTime expiresAt) {
        if (order == null || provider == null || amount == null || amount.signum() <= 0
                || currency == null || currency.isBlank()
                || paymentIntentId == null || paymentIntentId.isBlank()
                || clientToken == null || clientToken.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("Valid order, provider, amount, currency, intent, token and expiry are required");
        }
        this.order = order;
        this.provider = provider;
        this.amount = amount;
        this.currency = currency.trim().toUpperCase();
        this.paymentIntentId = paymentIntentId;
        this.clientToken = clientToken;
        this.expiresAt = expiresAt;
        this.status = PaymentStatus.INITIATED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void markPending() {
        requireStatus(PaymentStatus.INITIATED);
        this.status = PaymentStatus.PENDING;
        touch();
    }

    public void markSucceeded(String gatewayReference) {
        if (gatewayReference == null || gatewayReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Gateway reference is required for successful payment"
            );
        }

        if (status == PaymentStatus.SUCCEEDED) {
            return;
        }

        requireStatus(
                PaymentStatus.PENDING,
                PaymentStatus.INITIATED,
                PaymentStatus.EXPIRED
        );

        this.gatewayReference = gatewayReference;
        this.failureReason = null;
        this.status = PaymentStatus.SUCCEEDED;
        touch();
    }

    public void markFailed(String reason) {
        if (status == PaymentStatus.FAILED) {
            return;
        }
        requireStatus(PaymentStatus.PENDING, PaymentStatus.INITIATED);
        this.failureReason = normalizeReason(reason);
        this.status = PaymentStatus.FAILED;
        touch();
    }

    public void markExpired() {
        if (status == PaymentStatus.EXPIRED) {
            return;
        }
        requireStatus(PaymentStatus.PENDING, PaymentStatus.INITIATED);
        this.status = PaymentStatus.EXPIRED;
        touch();
    }

    private void requireStatus(PaymentStatus... allowedStatuses) {
        for (PaymentStatus allowedStatus : allowedStatuses) {
            if (status == allowedStatus) {
                return;
            }
        }
        throw new IllegalStateException("Payment attempt cannot transition from status: " + status);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Payment failed";
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
