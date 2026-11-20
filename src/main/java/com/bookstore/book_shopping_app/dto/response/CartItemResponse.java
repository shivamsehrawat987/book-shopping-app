package com.bookstore.book_shopping_app.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long bookId,
        String bookTitle,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal
) {
}