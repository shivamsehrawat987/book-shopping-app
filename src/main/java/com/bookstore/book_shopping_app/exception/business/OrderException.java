package com.bookstore.book_shopping_app.exception.business;

public class OrderException extends CommonException {
    public OrderException(String code, String message) {
        super(code, message);
    }
}