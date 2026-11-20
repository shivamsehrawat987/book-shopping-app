package com.bookstore.book_shopping_app.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @NotBlank
    @Size(max = 200)
    @Column(name = "book_title_at_purchase", nullable = false, length = 200)
    private String bookTitleAtPurchase;

    @NotNull
    @Positive(message = "Quantity must be greater than zero")
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(name = "price_at_purchase", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtPurchase;

    OrderItem(Order order, Book book, Integer quantity) {
        if (order == null || book == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Order, book, and positive quantity are required");
        }
        if (book.getTitle() == null || book.getTitle().isBlank()
                || book.getPrice() == null || book.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Book must have a title and positive price");
        }
        this.order = order;
        this.book = book;
        this.bookTitleAtPurchase = book.getTitle();
        this.quantity = quantity;
        this.priceAtPurchase = book.getPrice();
    }
}