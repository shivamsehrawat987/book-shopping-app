
package com.bookstore.book_shopping_app.entity;

import java.math.BigDecimal;

import com.bookstore.book_shopping_app.enums.BookCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Book title is required")
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank(message = "Author name is required")
    @Column(nullable = false, length = 150)
    private String author;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock cannot be negative")
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(length = 2000)
    private String description;

    @NotNull(message = "Book category is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BookCategory category;

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
        this.title = title;
        this.author = author;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.description = description;
        this.category = category;
    }

    public void updatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        this.price = price;
    }

    public void updateDetails(String title, String author, String description) {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("Title must contain between 1 and 200 characters");
        }
        if (author == null || author.isBlank() || author.length() > 150) {
            throw new IllegalArgumentException("Author must contain between 1 and 150 characters");
        }
        if (description == null || description.isBlank() || description.length() > 2000) {
            throw new IllegalArgumentException("Description must contain between 1 and 2000 characters");
        }
        this.title = title;
        this.author = author;
        this.description = description;
    }

    public void updateStock(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        this.stockQuantity = quantity;
    }

    public void updateCategory(BookCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("Book category is required");
        }
        this.category = category;
    }
}
