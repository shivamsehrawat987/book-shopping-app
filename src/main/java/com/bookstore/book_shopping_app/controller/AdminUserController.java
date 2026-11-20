package com.bookstore.book_shopping_app.controller;

import com.bookstore.book_shopping_app.dto.request.RegisterRequest;
import com.bookstore.book_shopping_app.dto.response.UserResponse;
import com.bookstore.book_shopping_app.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "basicAuth")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createAdmin(@Valid @RequestBody RegisterRequest request) {
        return authService.createAdmin(request);
    }
}