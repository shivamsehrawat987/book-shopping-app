package com.bookstore.book_shopping_app.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import com.bookstore.book_shopping_app.exception.business.CommonException;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderRepository orders;
    private final BookRepository books;
    private final PaymentAttemptRepository paymentAttempts;
    private final PaymentAttemptLifecycleService paymentAttemptLifecycle;
    private final Clock clock;

    @Transactional
    public void expireOrder(Long orderId) {
        Order order = orders.findLockedById(orderId)
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.ORDER_NOT_FOUND,
                        BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE));

        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getExpiresAt() == null || order.getExpiresAt().isAfter(now)) {
            return;
        }
        expirePendingOrder(order);
    }

    /** Must be called inside the caller's transaction while the order row is locked. */
    public void expirePendingOrder(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        // Deterministic lock order prevents deadlocks for orders sharing multiple books.
        order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getBook().getId()))
                .forEach(item -> {
                    var book = books.findLockedById(item.getBook().getId())
                            .orElseThrow(() -> new CommonException(
                                    BookstoreErrorConstants.BOOK_NOT_FOUND,
                                    BookstoreMessageConstants.BOOK_NOT_FOUND_WHILE_RESTORING_STOCK_MESSAGE));
                    int restoredStock = Math.addExact(book.getStockQuantity(), item.getQuantity());
                    book.updateStock(restoredStock);
                });

        paymentAttempts.findByOrder_IdOrderByCreatedAtDesc(order.getId())
                .forEach(attempt -> {
                    if (attempt.getStatus() == PaymentStatus.INITIATED
                            || attempt.getStatus() == PaymentStatus.PENDING) {
                        paymentAttemptLifecycle.markExpired(attempt);
                    }
                });

        order.expire();
    }
}
