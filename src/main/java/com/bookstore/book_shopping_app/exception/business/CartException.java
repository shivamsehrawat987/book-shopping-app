package com.bookstore.book_shopping_app.exception.business;

public class CartException extends CommonException {
    public CartException(String code, String message) {
        super(code, message);
    }
}