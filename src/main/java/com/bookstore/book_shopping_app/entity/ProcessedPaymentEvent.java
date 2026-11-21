package com.bookstore.book_shopping_app.entity;

import java.time.LocalDateTime;

import com.bookstore.book_shopping_app.enums.PaymentEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_payment_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_payment_event_id", columnNames = "event_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProcessedPaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private PaymentEventType eventType;

    @NotBlank
    @Column(name = "payment_intent_id", nullable = false, length = 100)
    private String paymentIntentId;

    @NotBlank
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @NotNull
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;
}
