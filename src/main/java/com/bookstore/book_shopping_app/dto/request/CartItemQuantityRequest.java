package com.bookstore.book_shopping_app.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CartItemQuantityRequest(@NotNull @Positive Integer quantity) {
}
