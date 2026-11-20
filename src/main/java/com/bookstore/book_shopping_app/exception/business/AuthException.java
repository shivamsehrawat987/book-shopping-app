package com.bookstore.book_shopping_app.exception.business;

public class AuthException extends CommonException {
    public AuthException(String code, String message) {
        super(code, message);
    }
}