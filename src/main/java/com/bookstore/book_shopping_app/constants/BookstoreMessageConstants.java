
package com.bookstore.book_shopping_app.constants;

public final class BookstoreMessageConstants {

    private BookstoreMessageConstants() {
        // Prevent object creation
    }

    // General API error messages
    public static final String INTERNAL_ERROR_MESSAGE =
            "An unexpected error occurred. Please try again later.";

    public static final String REQUEST_REJECTED_MESSAGE =
            "The request could not be completed.";

    public static final String VALIDATION_FAILED_MESSAGE =
            "One or more request fields are invalid.";

    public static final String INVALID_PARAMETER_MESSAGE =
            "The supplied parameter is invalid.";

    public static final String INVALID_REQUEST_BODY_MESSAGE =
            "The request body is missing, malformed, or contains an unsupported value.";

    public static final String MISSING_HEADER_MESSAGE =
            "A required request header is missing.";

    public static final String DATA_CONFLICT_MESSAGE =
            "The request conflicts with existing data. Refresh and try again.";

    // Book messages
    public static final String BOOK_NOT_FOUND_MESSAGE = "Book not found";

    // Cart messages
    public static final String CART_NOT_FOUND_MESSAGE = "Cart not found";
    public static final String CART_ITEM_NOT_FOUND_MESSAGE = "Cart item not found";

    public static final String CART_PENDING_CHECKOUT_MESSAGE =
            "Cart has a pending checkout";

    public static final String CART_EMPTY_MESSAGE =
            "Your cart is empty. Add a book before checkout.";

    public static final String INSUFFICIENT_STOCK_MESSAGE =
            "Insufficient stock available";

    // Cart stock validation messages
    public static final String ADDITIONAL_COPIES_MESSAGE = " more copies. ";

    public static final String COPIES_ALREADY_IN_CART_MESSAGE =
            " copies are already in your cart. ";

    public static final String AVAILABLE_STOCK_MESSAGE = "Available stock: ";

    public static final String MAXIMUM_COPIES_MESSAGE =
            "You can have a maximum of ";

    public static final String COPIES_OF_BOOK_MESSAGE =
            " copies of this book in your cart.";

    public static final String YOU_CAN_ADD_ONLY_MESSAGE =
            "You can add only ";

    // User messages
    public static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    public static final String ACCOUNT_ALREADY_EXISTS_MESSAGE =
            "An account already exists.";

    // Order messages
    public static final String ORDER_NOT_FOUND_MESSAGE = "Order not found.";

    public static final String ORDER_TOTAL_UNSUPPORTED_MESSAGE =
            "The cart total is outside the supported order amount.";

    // Checkout messages
    public static final String INVALID_IDEMPOTENCY_KEY_MESSAGE =
            "The Idempotency-Key header must contain between 1 and 100 characters.";

    public static final String CHECKOUT_ALREADY_PENDING_MESSAGE =
            "This cart already has a pending checkout.";

    public static final String IDEMPOTENCY_KEY_REUSED_MESSAGE =
            "This Idempotency-Key was already used with different checkout details.";

    public static final String CHECKOUT_NO_LONGER_PENDING_MESSAGE =
            "This checkout is no longer pending. Retrieve the order to see its current status.";

    public static final String BOOK_UNAVAILABLE_FOR_CHECKOUT_MESSAGE =
            "A book in your cart is no longer available. Refresh your cart and try again.";

    public static final String INSUFFICIENT_BOOK_STOCK_MESSAGE =
            "Only %d copies of '%s' are available; your cart requests %d. Update your cart and try again.";

    // Payment messages
    public static final String PAYMENT_INTENT_NOT_FOUND_MESSAGE =
            "Payment intent not found.";

    public static final String PAYMENT_CONFLICT_MESSAGE =
            "Payment conflict.";

    public static final String PAYMENT_GATEWAY_UNAVAILABLE_MESSAGE =
            "The payment gateway is currently unavailable.";

    public static final String INVALID_WEBHOOK_MESSAGE =
            "Invalid payment webhook.";

    public static final String PAYMENT_ATTEMPT_NOT_FOUND_MESSAGE =
            "Payment attempt not found";

    public static final String INVALID_ORDER_STATUS_MESSAGE =
            "Order cannot be marked as paid from the current status";

    public static final String PAYMENT_AMOUNT_MISMATCH_MESSAGE =
            "Webhook amount does not match the payment attempt";

    public static final String PAYMENT_CURRENCY_MISMATCH_MESSAGE =
            "Webhook currency does not match the payment attempt";

    public static final String UNSUPPORTED_PAYMENT_EVENT_MESSAGE =
            "Unsupported payment event type";

    public static final String EVENT_ID_REUSED_MESSAGE =
            "The event ID has already been used with different payment data";

    public static final String ORDER_EXPIRED_MESSAGE =
            "This order has expired. Please checkout again.";

    public static final String PAYMENT_ALREADY_INITIATED_MESSAGE =
            "An active payment attempt already exists for this order";

    public static final String PAYMENT_PROVIDER_NOT_SUPPORTED_MESSAGE =
            "Selected payment provider is not supported";

    public static final String INVALID_PAYMENT_GATEWAY_RESPONSE_MESSAGE =
            "Payment gateway returned an invalid response";

    public static final String BOOK_NOT_FOUND_WHILE_RESTORING_STOCK_MESSAGE =
            "Book not found while restoring reserved stock";

    public static final String PAYMENT_ONLY_FOR_PENDING_ORDER_MESSAGE =
            "Payment can only be initiated for a pending order";
}
