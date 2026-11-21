package com.bookstore.book_shopping_app.controller;

import java.security.Principal;

import com.bookstore.book_shopping_app.dto.request.CheckoutRequest;
import com.bookstore.book_shopping_app.dto.response.CheckoutResponse;
import com.bookstore.book_shopping_app.dto.response.OrderResponse;
import com.bookstore.book_shopping_app.service.OrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "basicAuth")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orders;

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(Principal principal, @RequestHeader("Idempotency-Key") String key,
                                     @Valid @RequestBody CheckoutRequest request) {
        return orders.checkout(principal.getName(), key, request);
    }

    @GetMapping("/{id}")
    public OrderResponse get(Principal principal, @PathVariable Long id) {
        return orders.getOrder(principal.getName(), id);
    }
}