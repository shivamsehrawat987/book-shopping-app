package com.bookstore.book_shopping_app.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "carts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_carts_user_id", columnNames = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    public Cart(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Cart user is required");
        }
        this.user = user;
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public CartItem addItem(Book book, Integer quantity) {
        if (book == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("A book and positive quantity are required");
        }
        for (CartItem item : items) {
            if (item.getBook().getId() != null && item.getBook().getId().equals(book.getId())) {
                item.updateQuantity(item.getQuantity() + quantity);
                return item;
            }
        }
        CartItem item = new CartItem(this, book, quantity);
        items.add(item);
        return item;
    }

    public void clearItems() {
        for (CartItem item : new ArrayList<>(items)) {
            removeItem(item);
        }
    }

    public void removeItem(CartItem item) {
        if (item != null && items.remove(item)) {
            item.detachFromCart();
        }
    }

    public void removePurchasedQuantity(Long bookId, Integer purchasedQuantity) {

        if (bookId == null || purchasedQuantity == null || purchasedQuantity <= 0) {
            throw new IllegalArgumentException("Book ID and positive purchased quantity are required");
        }

        for (CartItem item : new ArrayList<>(items)) {

            if (Objects.equals(item.getBook().getId(), bookId)) {

                if (item.getQuantity() <= purchasedQuantity) {
                    removeItem(item);
                } else {
                    item.updateQuantity(item.getQuantity() - purchasedQuantity);
                }

                return;
            }
        }
    }

}