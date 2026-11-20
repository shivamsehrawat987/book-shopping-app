package com.bookstore.book_shopping_app.payment.gateway;

import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.enums.PaymentMethod;

public interface PaymentGateway {

    PaymentMethod getProvider();

    PaymentResult initiatePayment(GatewayPaymentRequest request);
}