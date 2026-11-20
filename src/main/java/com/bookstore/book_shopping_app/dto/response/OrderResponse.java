package com.bookstore.book_shopping_app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.bookstore.book_shopping_app.enums.OrderStatus;

public record OrderResponse(
        Long id,
        Long userId,
        BigDecimal totalAmount,
        OrderStatus status,
        String shippingAddress,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String currency,
        String paymentIntentId,
        List<OrderItemResponse> items
) {
    public OrderResponse {
        items = List.copyOf(items);
    }
}