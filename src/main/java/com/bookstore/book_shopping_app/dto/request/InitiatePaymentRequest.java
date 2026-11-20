package com.bookstore.book_shopping_app.dto.request;

import com.bookstore.book_shopping_app.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull(message = "Payment provider is required")
        PaymentMethod provider
) {
}