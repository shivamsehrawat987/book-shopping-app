
package com.bookstore.book_shopping_app.constants;

public final class BookstoreErrorConstants {

    private BookstoreErrorConstants() {
        // Prevent object creation
    }

    // General API error codes
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String REQUEST_REJECTED = "REQUEST_REJECTED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";
    public static final String INVALID_REQUEST_BODY = "INVALID_REQUEST_BODY";
    public static final String MISSING_HEADER = "MISSING_HEADER";
    public static final String DATA_CONFLICT = "DATA_CONFLICT";

    // Book error codes
    public static final String BOOK_NOT_FOUND = "BOOK_NOT_FOUND";

    // Cart error codes
    public static final String CART_NOT_FOUND = "CART_NOT_FOUND";
    public static final String CART_ITEM_NOT_FOUND = "CART_ITEM_NOT_FOUND";
    public static final String CART_PENDING_CHECKOUT = "CART_PENDING_CHECKOUT";
    public static final String CART_EMPTY = "CART_EMPTY";
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

    // User error codes
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_ALREADY_EXISTS = "ACCOUNT_ALREADY_EXISTS";

    // Order error codes
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER_TOTAL_UNSUPPORTED = "ORDER_TOTAL_UNSUPPORTED";

    // Checkout error codes
    public static final String CHECKOUT_CONFLICT = "CHECKOUT_CONFLICT";
    public static final String INVALID_IDEMPOTENCY_KEY = "INVALID_IDEMPOTENCY_KEY";

    // Payment error codes
    public static final String PAYMENT_INTENT_NOT_FOUND = "PAYMENT_INTENT_NOT_FOUND";
    public static final String PAYMENT_CONFLICT = "PAYMENT_CONFLICT";
    public static final String PAYMENT_GATEWAY_UNAVAILABLE = "PAYMENT_GATEWAY_UNAVAILABLE";
    public static final String INVALID_WEBHOOK = "INVALID_WEBHOOK";
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    public static final String PAYMENT_ATTEMPT_NOT_FOUND = "PAYMENT_ATTEMPT_NOT_FOUND";
    public static final String INVALID_ORDER_STATUS = "INVALID_ORDER_STATUS";
    public static final String PAYMENT_AMOUNT_MISMATCH = "PAYMENT_AMOUNT_MISMATCH";
    public static final String PAYMENT_CURRENCY_MISMATCH = "PAYMENT_CURRENCY_MISMATCH";
    public static final String UNSUPPORTED_PAYMENT_EVENT = "UNSUPPORTED_PAYMENT_EVENT";
    public static final String EVENT_ID_REUSED = "EVENT_ID_REUSED";
    public static final String ORDER_EXPIRED = "ORDER_EXPIRED";
    public static final String PAYMENT_ALREADY_INITIATED = "PAYMENT_ALREADY_INITIATED";
    public static final String PAYMENT_PROVIDER_NOT_SUPPORTED = "PAYMENT_PROVIDER_NOT_SUPPORTED";
    public static final String INVALID_PAYMENT_GATEWAY_RESPONSE = "INVALID_PAYMENT_GATEWAY_RESPONSE";

}
