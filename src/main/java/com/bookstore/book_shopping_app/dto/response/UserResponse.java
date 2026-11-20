package com.bookstore.book_shopping_app.dto.response;

import java.time.LocalDateTime;

import com.bookstore.book_shopping_app.enums.Role;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        LocalDateTime createdAt
) {
}