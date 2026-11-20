package com.bookstore.book_shopping_app.payment.adapter;

import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import com.bookstore.book_shopping_app.payment.gateway.GatewayPaymentRequest;
import com.bookstore.book_shopping_app.payment.gateway.PaymentGateway;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaytmAdapter implements PaymentGateway {

    @Override
    public PaymentMethod getProvider() {
        return PaymentMethod.PAYTM;
    }

    @Override
    public PaymentResult initiatePayment(GatewayPaymentRequest request) {
        String paymentIntentId = "PAYTM-" + UUID.randomUUID();
        String clientToken = UUID.randomUUID().toString();

        return new PaymentResult(
                paymentIntentId,
                clientToken,
                PaymentMethod.PAYTM,
                PaymentStatus.PENDING
        );
    }
}