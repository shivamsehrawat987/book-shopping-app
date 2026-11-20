package com.bookstore.book_shopping_app.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bookstore.book_shopping_app.entity.PaymentAttempt;
import com.bookstore.book_shopping_app.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByPaymentIntentId(String paymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentAttempt p join fetch p.order where p.paymentIntentId = :intentId")
    Optional<PaymentAttempt> findLockedByPaymentIntentId(@Param("intentId") String intentId);

    List<PaymentAttempt> findByOrder_IdOrderByCreatedAtDesc(Long orderId);

    List<PaymentAttempt> findByStatusInAndExpiresAtBefore(
            List<PaymentStatus> statuses, LocalDateTime now);
}
