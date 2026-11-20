package com.bookstore.book_shopping_app.dto.response;

import java.math.BigDecimal;

import com.bookstore.book_shopping_app.enums.BookCategory;

public record BookResponse(
        Long id,
        String title,
        String author,
        BigDecimal price,
        Integer stockQuantity,
        String description,
        BookCategory category
) {
}