package com.bookstore.book_shopping_app.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.CartItem;
import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.entity.OrderItem;

/** Creates an order and its immutable-at-purchase item snapshots from a cart. */
@Component
public class OrderFactory {

    public Order createFrom(
            Cart cart,
            Map<Long, Book> reservedBooks,
            String checkoutKey,
            String shippingAddress,
            LocalDateTime expiresAt) {

        BigDecimal total = BigDecimal.ZERO;

        var items = cart.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getBook().getId()))
                .toList();

        for (CartItem cartItem : items) {
            Book book = reservedBooks.get(cartItem.getBook().getId());
            if (book == null) {
                throw new IllegalStateException(
                        "A cart book was not locked and reserved before order creation");
            }
            validateBookSnapshot(book);

            total = total.add(book.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        Order order = Order.builder()
                .user(cart.getUser())
                .shippingAddress(shippingAddress.trim())
                .cartId(cart.getId())
                .checkoutKey(checkoutKey)
                .expiresAt(expiresAt)
                .currency("INR")
                .totalAmount(total)
                .build();

        for (CartItem cartItem : items) {
            Book book = reservedBooks.get(cartItem.getBook().getId());
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .book(book)
                    .bookTitleAtPurchase(book.getTitle())
                    .quantity(cartItem.getQuantity())
                    .priceAtPurchase(book.getPrice())
                    .build();
            order.getItems().add(orderItem);
        }

        return order;
    }

    private void validateBookSnapshot(Book book) {
        if (book.getTitle() == null || book.getTitle().isBlank()
                || book.getPrice() == null
                || book.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Book must have a title and positive price");
        }
    }
}
