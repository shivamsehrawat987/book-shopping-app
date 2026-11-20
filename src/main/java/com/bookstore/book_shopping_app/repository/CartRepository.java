package com.bookstore.book_shopping_app.repository;

import java.util.Optional;

import com.bookstore.book_shopping_app.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @EntityGraph(attributePaths = {"items", "items.book"})
    Optional<Cart> findByUserEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cart> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "items.book"})
    Optional<Cart> findLockedByUserEmail(String email);
}