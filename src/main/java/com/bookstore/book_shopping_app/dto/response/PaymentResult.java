package com.bookstore.book_shopping_app.dto.response;

import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentResult(

        @NotBlank(message = "Payment intent ID is required")
        String paymentIntentId,

        @NotBlank(message = "Client token is required")
        String clientToken,

        @NotNull(message = "Payment provider is required")
        PaymentMethod provider,

        @NotNull(message = "Payment status is required")
        PaymentStatus status

) {
}