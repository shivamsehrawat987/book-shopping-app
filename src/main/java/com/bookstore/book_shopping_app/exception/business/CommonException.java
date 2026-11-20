package com.bookstore.book_shopping_app.exception.business;

public class CommonException extends RuntimeException {
    private final String code;

    public CommonException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}