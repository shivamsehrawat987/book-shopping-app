
package com.bookstore.book_shopping_app.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventoryAdjustmentRequest(
        @NotNull
        @Positive
        Integer quantity,

        @NotNull
        AdjustmentType type
) {
    public enum AdjustmentType {
        ADD,
        REMOVE
    }

    public int quantityChange() {
        return type == AdjustmentType.ADD ? quantity : -quantity;
    }
}
