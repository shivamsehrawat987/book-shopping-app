package com.bookstore.book_shopping_app.controller;

import com.bookstore.book_shopping_app.dto.request.InitiatePaymentRequest;
import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.payment.MockWebhookVerifier;
import com.bookstore.book_shopping_app.service.PaymentService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final MockWebhookVerifier verifier;
    private final PaymentService payments;

    @PostMapping("/orders/{orderId}/initiate")
    @SecurityRequirement(name = "basicAuth")
    public PaymentResult initiatePayment(
            Principal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody InitiatePaymentRequest request
    ) {
        return payments.initiatePayment(
                principal.getName(),
                orderId,
                request.provider()
        );
    }

    @PostMapping(
            value = "/webhook",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public void webhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Mock-Timestamp") String timestamp,
            @RequestHeader("X-Mock-Signature") String signature
    ) {
        var event = verifier.verify(rawBody, timestamp, signature);
        payments.processEvent(event);
    }
}