
package com.bookstore.book_shopping_app.dto.request;

import java.math.BigDecimal;

import com.bookstore.book_shopping_app.enums.BookCategory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 150) String author,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,
        @Size(max = 2000) String description,
        @NotNull BookCategory category,
        @NotNull Long version
) {
}
