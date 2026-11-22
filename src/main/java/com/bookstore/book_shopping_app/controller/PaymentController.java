package com.bookstore.book_shopping_app.controller;

import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.dto.request.InitiatePaymentRequest;
import com.bookstore.book_shopping_app.dto.response.PaymentInitiationResponse;
import com.bookstore.book_shopping_app.dto.response.PaymentResult;
import com.bookstore.book_shopping_app.payment.MockPaymentEvent;
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

import static com.bookstore.book_shopping_app.constants.BookstoreMessageConstants.CHECKOUT_COMPLETED_SUCCESSFULLY;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final MockWebhookVerifier verifier;
    private final PaymentService payments;

    @PostMapping("/orders/{orderId}/initiate")
    @SecurityRequirement(name = "basicAuth")
    public PaymentInitiationResponse initiatePayment(
            Principal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody InitiatePaymentRequest request
    ) {

        PaymentResult result = payments.initiatePayment(
                principal.getName(),
                orderId,
                request.provider()
        );

        return new PaymentInitiationResponse(
                result.paymentIntentId(),
                result.clientToken(),
                result.provider(),
                result.status(),
                CHECKOUT_COMPLETED_SUCCESSFULLY
        );
    }

    @PostMapping(
            value = "/webhook",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = BookstoreMessageConstants.WEBHOOK_DESCRIPTION,
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = MockPaymentEvent.class
                    )
            )
    )
    public void webhook(
            @RequestBody String rawBody,
            @RequestHeader(
                    value = BookstoreMessageConstants.MOCK_TIMESTAMP_HEADER,
                    required = false
            ) String timestamp,
            @RequestHeader(
                    value = BookstoreMessageConstants.MOCK_SIGNATURE_HEADER,
                    required = false
            ) String signature
    ) {
        var event = verifier.verify(rawBody, timestamp, signature);
        payments.processEvent(event);
    }


}