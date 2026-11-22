package com.bookstore.book_shopping_app.constants;

public final class PaymentWebhookConstants {

    private PaymentWebhookConstants() {
        // Utility class
    }

    // Cryptography
    public static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String UTF_8 = "UTF-8";
    public static final long TIMESTAMP_TOLERANCE_SECONDS = 300L;
    public static final String SIGNATURE_SEPARATOR = ".";

    // Event types
    public static final String EVENT_PAYMENT_SUCCEEDED =
            "payment_intent.succeeded";

    public static final String EVENT_PAYMENT_FAILED =
            "payment_intent.payment_failed";

    // Error codes
    public static final String INVALID_WEBHOOK = "INVALID_WEBHOOK";

    // Error messages
    public static final String WEBHOOK_SECRET_REQUIRED =
            "Configure app.payment.mock.webhook-secret before starting the application";

    public static final String WEBHOOK_SECRET_TOO_SHORT =
            "Mock webhook secret must be at least 32 bytes";

    public static final String INVALID_WEBHOOK_JSON =
            "Invalid webhook JSON";

    public static final String WEBHOOK_EVENT_NULL =
            "Webhook event is null";

    public static final String INVALID_MOCK_PAYMENT_EVENT =
            "Invalid mock payment event";

    public static final String UNSUPPORTED_EVENT_TYPE =
            "Unsupported simulated event type";

    public static final String SIGNATURE_HEADERS_REQUIRED =
            "Webhook timestamp and signature headers are required";

    public static final String INVALID_SIGNATURE =
            "Invalid or expired mock webhook signature";

    public static final String VERIFICATION_FAILURE =
            "Cannot verify webhook";
    public static final String PAYMENT_SUCCEEDED_EVENT = "payment_intent.succeeded";
    public static final String PAYMENT_FAILED_EVENT = "payment_intent.payment_failed";
    public static final String PAYLOAD_SEPARATOR = "|";

}

