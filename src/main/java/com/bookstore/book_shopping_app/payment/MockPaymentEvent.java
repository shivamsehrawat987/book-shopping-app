
package com.bookstore.book_shopping_app.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MockPaymentEvent(

        @NotBlank(message = "Event ID is required")
        @Size(max = 120)
        String eventId,

        @NotBlank(message = "Event type is required")
        @Pattern(
                regexp = "payment_intent\\.(succeeded|payment_failed)",
                message = "Unsupported payment event type"
        )
        String type,

        @NotBlank(message = "Payment intent ID is required")
        @Size(max = 100)
        String paymentIntentId,

        @NotNull(message = "Payment amount is required")
        @Positive(message = "Payment amount must be positive")
        Long amountMinor,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "[A-Za-z]{3}")
        String currency,

        @Size(max = 150)
        String gatewayReference,

        @Size(max = 500)
        String failureReason
) {
}
