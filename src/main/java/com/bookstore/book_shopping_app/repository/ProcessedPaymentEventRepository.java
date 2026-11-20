package com.bookstore.book_shopping_app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookstore.book_shopping_app.entity.ProcessedPaymentEvent;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, Long> {

    Optional<ProcessedPaymentEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
