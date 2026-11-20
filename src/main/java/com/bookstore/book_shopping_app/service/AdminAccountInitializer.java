package com.bookstore.book_shopping_app.service;

import java.util.Locale;

import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.Role;
import com.bookstore.book_shopping_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;

    public AdminAccountInitializer(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap.enabled:false}") boolean enabled,
            @Value("${app.admin.email:}") String email,
            @Value("${app.admin.password:}") String password
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (email.isBlank() || password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("Configure both app.admin.email and a 12-72 character app.admin.password");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(normalizedEmail).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ROLE_ADMIN) {
                throw new IllegalStateException("Configured admin email belongs to a non-admin account");
            }
        }, () -> users.save(new User(
                normalizedEmail,
                passwordEncoder.encode(password),
                "Administrator",
                Role.ROLE_ADMIN
        )));
    }
}