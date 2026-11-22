package com.bookstore.book_shopping_app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CheckoutResponse(
        Long orderId,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime expiresAt,
        boolean paymentRequired,
        LocalDate estimatedDeliveryDate,
        String message
) {
}