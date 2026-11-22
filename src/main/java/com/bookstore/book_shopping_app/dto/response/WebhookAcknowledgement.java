package com.bookstore.book_shopping_app.dto.response;

public record WebhookAcknowledgement(
        String status,
        String message
) {
}