package com.bookstore.book_shopping_app.service;

import com.bookstore.book_shopping_app.dto.request.CheckoutRequest;
import com.bookstore.book_shopping_app.dto.response.CheckoutResponse;
import com.bookstore.book_shopping_app.dto.response.OrderResponse;

public interface OrderService {
    CheckoutResponse checkout(String email, String idempotencyKey, CheckoutRequest request);
    OrderResponse getOrder(String email, Long id);
}