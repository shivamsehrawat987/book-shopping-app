package com.bookstore.book_shopping_app.controller;

import com.bookstore.book_shopping_app.dto.request.InitiatePaymentRequest;
import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.payment.MockWebhookVerifier;
import com.bookstore.book_shopping_app.service.PaymentService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Mock payment event. The raw JSON is retained for HMAC signature verification.",
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = com.bookstore.book_shopping_app.payment.MockPaymentEvent.class)
            )
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