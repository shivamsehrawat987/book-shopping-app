package com.bookstore.book_shopping_app.controller;

import java.security.Principal;

import com.bookstore.book_shopping_app.dto.request.CartItemQuantityRequest;
import com.bookstore.book_shopping_app.dto.request.CartItemRequest;
import com.bookstore.book_shopping_app.dto.response.CartResponse;
import com.bookstore.book_shopping_app.service.CartService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@SecurityRequirement(name = "basicAuth")
@RequiredArgsConstructor
public class CartController {
    private final CartService carts;

    @GetMapping
    public CartResponse get(Principal principal) {
        return carts.getCart(principal.getName());
    }

    @PostMapping("/items")
    public CartResponse add(Principal principal, @Valid @RequestBody CartItemRequest request) {
        return carts.addItem(principal.getName(), request);
    }
    @PutMapping("/items/{itemId}")
    public CartResponse updateQuantity(Principal principal, @PathVariable Long itemId,
                                       @Valid @RequestBody CartItemQuantityRequest request) {
        return carts.updateItemQuantity(principal.getName(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse remove(Principal principal, @PathVariable Long itemId) {
        return carts.removeItem(principal.getName(), itemId);
    }

    @DeleteMapping
    public CartResponse clear(Principal principal) {
        return carts.clearCart(principal.getName());
    }
}
