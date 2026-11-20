package com.bookstore.book_shopping_app.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long id,
        Long userId,
        List<CartItemResponse> items,
        BigDecimal totalAmount
) {
    public CartResponse {
        items = List.copyOf(items);
    }
}