package com.bookstore.book_shopping_app.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank @Size(max = 1000) String shippingAddress,
        @NotNull @Positive Long cartId
) {
}