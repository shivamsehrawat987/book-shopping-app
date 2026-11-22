package com.bookstore.book_shopping_app.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.bookstore.book_shopping_app.enums.OrderStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_user_checkout", columnNames = {"user_id", "checkout_key"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private OrderStatus status = OrderStatus.PENDING;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "shipping_address", nullable = false, length = 1000)
    private String shippingAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "checkout_key", length = 100)
    private String checkoutKey;

    private LocalDateTime expiresAt;

    @Column(unique = true)
    private String paymentIntentId;

    @Column(length = 3)
    private String currency;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /** Marks an order paid only while its stock reservation is still active. */
    public void markPaid() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be marked as paid");
        }
        this.status = OrderStatus.PAID;
    }

    /** Expires a pending order after its reserved stock has been restored. */
    public void expire() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be expired");
        }
        this.status = OrderStatus.EXPIRED;
    }

    /** Cancels a pending order after its reserved stock has been restored. */
    public void cancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
    }

    /** Records that money was captured after the order's stock reservation expired. */
    public void requireReconciliation() {
        if (this.status != OrderStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Only expired orders can require payment reconciliation");
        }
        this.status = OrderStatus.REQUIRES_RECONCILIATION;
    }
}
