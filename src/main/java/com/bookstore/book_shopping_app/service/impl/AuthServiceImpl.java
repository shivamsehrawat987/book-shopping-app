// This file has been created (totally or partially) with the assistance of artificial intelligence tools.
// All content has been generated under the direct supervision of a named individual,
// and under the AI.Backbone Orchestrator Compliance framework

package com.bookstore.book_shopping_app.service.impl;

import java.util.Locale;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.dto.request.RegisterRequest;
import com.bookstore.book_shopping_app.dto.response.UserResponse;
import com.bookstore.book_shopping_app.enums.Role;
import com.bookstore.book_shopping_app.exception.business.AuthException;
import com.bookstore.book_shopping_app.mapper.UserMapper;
import com.bookstore.book_shopping_app.repository.UserRepository;
import com.bookstore.book_shopping_app.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        return createUser(request, Role.ROLE_CUSTOMER);
    }

    @Override
    @Transactional
    public UserResponse createAdmin(RegisterRequest request) {
        return createUser(request, Role.ROLE_ADMIN);
    }

    private UserResponse createUser(RegisterRequest request, Role role) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new AuthException(BookstoreErrorConstants.ACCOUNT_ALREADY_EXISTS, "An account with this email already exists");
        }
        UserResponse response = mapper.toResponse(users.save(
                mapper.toEntity(request, passwordEncoder.encode(request.password()), role)));
        log.info("User account created: userId={}, role={}", response.id(), role);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        UserResponse response = users.findByEmailIgnoreCase(email)
                .map(mapper::toResponse)
                .orElseThrow(() -> new AuthException(BookstoreErrorConstants.INVALID_CREDENTIALS, "Invalid credentials"));
        log.info("User profile retrieved: userId={}", response.id());
        return response;
    }
}