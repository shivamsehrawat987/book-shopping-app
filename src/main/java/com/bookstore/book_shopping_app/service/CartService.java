package com.bookstore.book_shopping_app.service;

import com.bookstore.book_shopping_app.dto.request.CartItemQuantityRequest;
import com.bookstore.book_shopping_app.dto.request.CartItemRequest;
import com.bookstore.book_shopping_app.dto.response.CartResponse;

public interface CartService {
    CartResponse getCart(String email);
    CartResponse addItem(String email, CartItemRequest request);
    CartResponse updateItemQuantity(String email, Long itemId, CartItemQuantityRequest request);
    CartResponse removeItem(String email, Long itemId);
    CartResponse clearCart(String email);
}