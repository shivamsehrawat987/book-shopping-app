package com.bookstore.book_shopping_app.controller;

import java.security.Principal;

import com.bookstore.book_shopping_app.dto.response.CheckoutRequest;
import com.bookstore.book_shopping_app.dto.response.CheckoutResponse;
import com.bookstore.book_shopping_app.dto.response.OrderResponse;
import com.bookstore.book_shopping_app.service.OrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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