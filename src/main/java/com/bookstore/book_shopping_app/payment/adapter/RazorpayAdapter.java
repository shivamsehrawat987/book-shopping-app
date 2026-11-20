
package com.bookstore.book_shopping_app.payment.adapter;

import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import com.bookstore.book_shopping_app.payment.gateway.GatewayPaymentRequest;
import com.bookstore.book_shopping_app.payment.gateway.PaymentGateway;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RazorpayAdapter implements PaymentGateway {

    @Override
    public PaymentMethod getProvider() {
        return PaymentMethod.RAZORPAY;
    }

    @Override
    public PaymentResult initiatePayment(GatewayPaymentRequest request) {
        String paymentIntentId = "RAZORPAY-" + UUID.randomUUID();
        String clientToken = UUID.randomUUID().toString();

        return new PaymentResult(
                paymentIntentId,
                clientToken,
                PaymentMethod.RAZORPAY,
                PaymentStatus.PENDING
        );
    }
}
