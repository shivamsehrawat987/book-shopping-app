package com.bookstore.book_shopping_app.enums;


public enum PaymentMethod {

    PAYTM,
    PHONEPE,
    RAZORPAY,

    // Keep temporarily because the existing Stripe strategy
    // still references this value. We will remove it later.
    STRIPE
}