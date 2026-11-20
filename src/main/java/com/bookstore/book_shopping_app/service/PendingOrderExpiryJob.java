package com.bookstore.book_shopping_app.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingOrderExpiryJob {
    private final OrderRepository orders;
    private final PaymentService payments;
    private final Clock clock;

        @Scheduled(initialDelayString = "${app.checkout.expiry-scan-ms:60000}",
            fixedDelayString = "${app.checkout.expiry-scan-ms:60000}")
    public void expirePendingOrders() {
        for (Long id : orders.findExpiredIds(OrderStatus.PENDING, LocalDateTime.now(clock))) {
            try {
                payments.expireOrder(id);
            } catch (RuntimeException exception) {
                log.warn("Could not expire order {}; will retry on next scan", id, exception);
            }
        }
    }
}