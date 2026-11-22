package com.bookstore.book_shopping_app.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUserIdAndCheckoutKey(Long userId, String key);

    @EntityGraph(attributePaths = {"items", "items.book"})
    Optional<Order> findByIdAndUserEmailIgnoreCase(Long id, String email);

    boolean existsByCartIdAndStatus(Long cartId, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(Long id);

    @Query("select o.id from Order o where o.status = :status and o.expiresAt <= :now order by o.id")
    List<Long> findExpiredIds(@Param("status") OrderStatus status, @Param("now") LocalDateTime now);
}