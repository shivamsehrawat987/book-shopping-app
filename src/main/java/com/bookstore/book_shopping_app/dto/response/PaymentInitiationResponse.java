package com.bookstore.book_shopping_app.dto.response;

import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.enums.PaymentStatus;

public record PaymentInitiationResponse(
        String paymentIntentId,
        String clientToken,
        PaymentMethod provider,
        PaymentStatus status,
        String message
) {
}