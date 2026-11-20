package com.bookstore.book_shopping_app.service;

import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.payment.MockPaymentEvent;

public interface PaymentService {

    PaymentResult initiatePayment(
            String email,
            Long orderId,
            PaymentMethod provider
    );

    void processEvent(MockPaymentEvent event);

    void expireOrder(Long orderId);
}
