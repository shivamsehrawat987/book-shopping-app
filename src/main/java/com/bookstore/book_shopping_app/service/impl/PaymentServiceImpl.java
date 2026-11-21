package com.bookstore.book_shopping_app.service.impl;

import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.payment.MockPaymentEvent;
import com.bookstore.book_shopping_app.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Thin application facade. Payment workflows live in focused services. */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentInitiationService paymentInitiationService;
    private final PaymentWebhookService paymentWebhookService;
    private final OrderExpirationService orderExpirationService;

    @Override
    public PaymentResult initiatePayment(String email, Long orderId, PaymentMethod provider) {
        return paymentInitiationService.initiatePayment(email, orderId, provider);
    }

    @Override
    public void processEvent(MockPaymentEvent event) {
        paymentWebhookService.processEvent(event);
    }

    @Override
    public void expireOrder(Long orderId) {
        orderExpirationService.expireOrder(orderId);
    }
}
