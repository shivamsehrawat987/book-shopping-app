package com.bookstore.book_shopping_app.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(name = "uk_order_user_checkout", columnNames = {"user_id", "checkout_key"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "shipping_address", nullable = false, length = 1000)
    private String shippingAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
    private List<OrderItem> items = new ArrayList<>();

    public Order(User user, String shippingAddress) {
        if (user == null || shippingAddress == null || shippingAddress.isBlank()) {
            throw new IllegalArgumentException("User and shipping address are required");
        }
        this.user = user;
        this.shippingAddress = shippingAddress;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderItem addItem(Book book, Integer quantity) {
        OrderItem item = new OrderItem(this, book, quantity);
        items.add(item);
        totalAmount = totalAmount.add(item.getPriceAtPurchase().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    public void updateStatus(OrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Order status is required");
        }
        this.status = status;
    }

    public void prepareCheckout(Long cartId, String checkoutKey, LocalDateTime expiresAt) {
        this.cartId = cartId;
        this.checkoutKey = checkoutKey;
        this.expiresAt = expiresAt;
        this.currency = "INR";
    }

    public void attachPaymentIntent(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank() || this.paymentIntentId != null) {
            throw new IllegalStateException("A single valid payment intent is required");
        }
        this.paymentIntentId = paymentIntentId;
    }

}