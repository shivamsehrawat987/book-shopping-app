package com.bookstore.book_shopping_app.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bookstore.book_shopping_app.enums.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 254)
    @Column(nullable = false, length = 254)
    private String email;

    @NotBlank(message = "Password hash is required")
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotBlank(message = "Full name is required")
    @Size(max = 150)
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role = Role.ROLE_CUSTOMER;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private Cart cart;

    public User(String email, String passwordHash, String fullName) {
        this(email, passwordHash, fullName, Role.ROLE_CUSTOMER);
    }

    @Builder
    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (email != null) {
            email = email.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }

}