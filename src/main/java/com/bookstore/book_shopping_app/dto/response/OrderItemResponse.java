package com.bookstore.book_shopping_app.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long bookId,
        String bookTitleAtPurchase,
        Integer quantity,
        BigDecimal priceAtPurchase,
        BigDecimal lineTotal
) {
}