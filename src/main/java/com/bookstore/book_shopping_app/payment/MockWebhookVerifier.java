
package com.bookstore.book_shopping_app.payment;

import com.bookstore.book_shopping_app.exception.business.CommonException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;

@Component
public class MockWebhookVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    private final byte[] secret;
    private final ObjectMapper json;
    private final Validator validator;
    private final Clock clock;

    public MockWebhookVerifier(
            @Value("${app.payment.mock.webhook-secret:}") String webhookSecret,
            ObjectMapper json,
            Validator validator,
            Clock clock
    ) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException(
                    "Configure app.payment.mock.webhook-secret before starting the application"
            );
        }

        this.secret = webhookSecret.getBytes(StandardCharsets.UTF_8);

        if (this.secret.length < 32) {
            throw new IllegalStateException(
                    "Mock webhook secret must be at least 32 bytes"
            );
        }

        if (this.secret.length < 32) {
            throw new IllegalStateException(
                    "Mock webhook secret must be at least 32 bytes"
            );
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

        verifySignature(rawBody, timestamp, signature);

        MockPaymentEvent event;

        try {
            event = json.readValue(rawBody, MockPaymentEvent.class);
        } catch (JacksonException exception) {
            throw new CommonException(
                    "INVALID_WEBHOOK",
                    "Invalid webhook JSON"
            );
        }

        if (event == null || !validator.validate(event).isEmpty()) {
            throw new CommonException(
                    "INVALID_WEBHOOK",
                    "Invalid mock payment event"
            );
        }

        if (!"payment_intent.succeeded".equals(event.type())
                && !"payment_intent.payment_failed".equals(event.type())) {

            throw new CommonException(
                    "INVALID_WEBHOOK",
                    "Unsupported simulated event type"
            );
        }

        return event;
    }

    private void verifySignature(
            String rawBody,
            String timestamp,
            String signature
    ) {

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
                    (timestamp + "." + rawBody)
                            .getBytes(StandardCharsets.UTF_8)
            );

            byte[] actual = HexFormat.of().parseHex(signature);

            if (!MessageDigest.isEqual(expected, actual)) {
                throw new IllegalArgumentException("Invalid signature");
            }

        } catch (IllegalArgumentException exception) {
            throw new CommonException(
                    "INVALID_WEBHOOK",
                    "Invalid or expired mock webhook signature"
            );

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Cannot verify webhook",
                    exception
            );
        }
    }
}
