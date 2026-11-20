package com.bookstore.book_shopping_app.service;

import com.bookstore.book_shopping_app.dto.request.RegisterRequest;
import com.bookstore.book_shopping_app.dto.response.UserResponse;
public interface AuthService {
    UserResponse register(RegisterRequest request);

    UserResponse createAdmin(RegisterRequest request);

    UserResponse currentUser(String email);
}