
package com.bookstore.book_shopping_app.payment;

import com.bookstore.book_shopping_app.exception.business.CommonException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

import static com.bookstore.book_shopping_app.constants.PaymentWebhookConstants.*;

@Component
public class MockWebhookVerifier {

    private static final Logger log =
            LoggerFactory.getLogger(MockWebhookVerifier.class);

    private final byte[] secret;
    private final boolean signatureRequired;
    private final ObjectMapper json;
    private final Validator validator;
    private final Clock clock;

    public MockWebhookVerifier(
            @Value("${app.payment.mock.webhook-secret:}") String webhookSecret,
            @Value("${app.payment.mock.signature-required:true}") boolean signatureRequired,
            ObjectMapper json,
            Validator validator,
            Clock clock
    ) {
        this.signatureRequired = signatureRequired;

        if (signatureRequired &&
                (webhookSecret == null || webhookSecret.isBlank())) {
            throw new IllegalStateException(WEBHOOK_SECRET_REQUIRED);
        }

        this.secret = webhookSecret == null
                ? new byte[0]
                : webhookSecret.getBytes(StandardCharsets.UTF_8);

        if (signatureRequired && this.secret.length < 32) {
            throw new IllegalStateException(WEBHOOK_SECRET_TOO_SHORT);
        }

        this.json = json;
        this.validator = validator;
        this.clock = clock;
    }

    public MockPaymentEvent verify(
            String rawBody,
            String timestamp,
            String signature
    ) {

        if (rawBody == null || rawBody.isBlank()) {
            throw new CommonException(
                    INVALID_WEBHOOK,
                    INVALID_WEBHOOK_JSON
            );
        }

        if (signatureRequired) {
            verifySignature(rawBody, timestamp, signature);
        }

        MockPaymentEvent event;

        try {
            event = json.readValue(rawBody, MockPaymentEvent.class);
        } catch (JacksonException exception) {
            log.warn("Webhook JSON deserialization failed", exception);

            throw new CommonException(
                    INVALID_WEBHOOK,
                    INVALID_WEBHOOK_JSON
            );
        }

        if (event == null) {
            throw new CommonException(
                    INVALID_WEBHOOK,
                    WEBHOOK_EVENT_NULL
            );
        }

        var violations = validator.validate(event);

        if (!violations.isEmpty()) {
            violations.forEach(violation ->
                    log.warn(
                            "Webhook validation failed: {} - {}",
                            violation.getPropertyPath(),
                            violation.getMessage()
                    )
            );

            throw new CommonException(
                    INVALID_WEBHOOK,
                    INVALID_MOCK_PAYMENT_EVENT
            );
        }

        if (!EVENT_PAYMENT_SUCCEEDED.equals(event.type())
                && !EVENT_PAYMENT_FAILED.equals(event.type())) {

            throw new CommonException(
                    INVALID_WEBHOOK,
                    UNSUPPORTED_EVENT_TYPE
            );
        }

        return event;
    }

    private void verifySignature(
            String rawBody,
            String timestamp,
            String signature
    ) {

        if (timestamp == null || timestamp.isBlank()
                || signature == null || signature.isBlank()) {

            throw new CommonException(
                    INVALID_WEBHOOK,
                    SIGNATURE_HEADERS_REQUIRED
            );
        }

        try {
            long signedAt = Long.parseLong(timestamp);
            long now = clock.instant().getEpochSecond();

            if (signedAt < now - TIMESTAMP_TOLERANCE_SECONDS
                    || signedAt > now + TIMESTAMP_TOLERANCE_SECONDS) {
                throw new IllegalArgumentException("Expired signature");
            }

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));

            byte[] expected = mac.doFinal(
                    (timestamp + SIGNATURE_SEPARATOR + rawBody)
                            .getBytes(StandardCharsets.UTF_8)
            );

            byte[] actual = HexFormat.of().parseHex(signature);

            if (!MessageDigest.isEqual(expected, actual)) {
                throw new IllegalArgumentException("Invalid signature");
            }

        } catch (IllegalArgumentException exception) {
            log.warn("Webhook signature verification failed: {}",
                    exception.getMessage());

            throw new CommonException(
                    INVALID_WEBHOOK,
                    INVALID_SIGNATURE
            );

        } catch (GeneralSecurityException exception) {
            log.error("Webhook signature verification could not be performed",
                    exception);

            throw new IllegalStateException(
                    VERIFICATION_FAILURE,
                    exception
            );
        }
    }
}
