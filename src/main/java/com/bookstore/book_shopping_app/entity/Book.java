
package com.bookstore.book_shopping_app.entity;

import java.math.BigDecimal;

import com.bookstore.book_shopping_app.enums.BookCategory;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String author;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotNull
    @PositiveOrZero
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BookCategory category;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @Builder
    public Book(
            String title,
            String author,
            BigDecimal price,
            Integer stockQuantity,
            String description,
            BookCategory category
    ) {
        this.title = title.trim();
        this.author = author.trim();
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.description = normalizeDescription(description);
        this.category = category;
    }

    public void updateDetails(
            String title,
            String author,
            BigDecimal price,
            String description,
            BookCategory category
    ) {
        this.title = title.trim();
        this.author = author.trim();
        this.price = price;
        this.description = normalizeDescription(description);
        this.category = category;
    }

    public void updateStock(int quantity) {
        this.stockQuantity = quantity;
    }

    public void adjustStock(int quantityChange) {
        if (quantityChange == 0) {
            throw new IllegalArgumentException("Stock adjustment cannot be zero");
        }

        int updatedStock = Math.addExact(stockQuantity, quantityChange);

        if (updatedStock < 0) {
            throw new IllegalArgumentException("Insufficient stock");
        }

        this.stockQuantity = updatedStock;
    }

    public void deactivate() {
        this.active = false;
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank()
                ? null
                : description.trim();
    }
}
