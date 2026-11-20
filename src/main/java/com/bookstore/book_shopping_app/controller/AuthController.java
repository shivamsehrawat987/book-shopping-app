package com.bookstore.book_shopping_app.controller;

import java.security.Principal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.bookstore.book_shopping_app.dto.request.RegisterRequest;
import com.bookstore.book_shopping_app.dto.response.UserResponse;
import com.bookstore.book_shopping_app.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirement(name = "basicAuth")
    public UserResponse login(Principal principal) {
        return authService.currentUser(principal.getName());
    }
}