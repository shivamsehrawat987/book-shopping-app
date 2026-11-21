
package com.bookstore.book_shopping_app;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.CartItem;
import com.bookstore.book_shopping_app.entity.PaymentAttempt;
import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.enums.PaymentMethod;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import com.bookstore.book_shopping_app.payment.MockPaymentEvent;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.CartRepository;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.repository.PaymentAttemptRepository;
import com.bookstore.book_shopping_app.repository.ProcessedPaymentEventRepository;
import com.bookstore.book_shopping_app.repository.UserRepository;
import com.bookstore.book_shopping_app.service.PaymentService;
import com.bookstore.book_shopping_app.service.PendingOrderExpiryJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-integration-tests;DB_CLOSE_DELAY=-1",
        "app.admin.bootstrap.enabled=false",
        "app.payment.mock.webhook-secret=payment-integration-tests-secret-at-least-32-bytes",
        "app.checkout.expiry-scan-ms=3600000"
})
@AutoConfigureMockMvc
class PaymentIntegrationTests {

    private static final String EMAIL = "payment-reader@example.test";
    private static final String OTHER_EMAIL = "payment-other@example.test";
    private static final String PASSWORD = "ReaderPassword123!";
    private static final String WEBHOOK_SECRET =
            "payment-integration-tests-secret-at-least-32-bytes";
    private static final Instant TEST_NOW =
            Instant.parse("2026-11-19T12:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private BookRepository books;
    @Autowired private CartRepository carts;
    @Autowired private OrderRepository orders;
    @Autowired private UserRepository users;
    @Autowired private PaymentAttemptRepository paymentAttempts;
    @Autowired private ProcessedPaymentEventRepository processedEvents;
    @Autowired private PaymentService payments;
    @Autowired private PendingOrderExpiryJob expiryJob;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private Clock clock;

    private Cart cart;
    private Book book;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(TEST_NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        processedEvents.deleteAll();
        paymentAttempts.deleteAll();
        orders.deleteAll();
        carts.deleteAll();
        books.deleteAll();
        users.deleteAll();

        User customer = users.save(new User(
                EMAIL, passwordEncoder.encode(PASSWORD), "Payment Reader"));

        users.save(new User(
                OTHER_EMAIL, passwordEncoder.encode(PASSWORD), "Other Reader"));

        book = books.saveAndFlush(new Book(
                "Payment Test Book",
                "Demo Author",
                new BigDecimal("299.00"),
                5,
                "Book used for payment integration tests",
                BookCategory.FICTION
        ));

        cart = carts.saveAndFlush(Cart.builder().user(customer).build());
        cart.getItems().add(CartItem.builder()
                .cart(cart)
                .book(book)
                .quantity(2)
                .build());
        cart = carts.saveAndFlush(cart);
    }

    @Test
    void successfulWebhookMarksPaymentAndOrderAsPaid() throws Exception {

        PaymentAttempt attempt = initiatePayment(PaymentMethod.PAYTM);

        MockPaymentEvent event = successEvent(attempt, "event-success-1");
        String body = json.writeValueAsString(event);

        postWebhook(body, sign(body), 200);

        PaymentAttempt updatedAttempt = paymentAttempts
                .findByPaymentIntentId(attempt.getPaymentIntentId())
                .orElseThrow();

        assertEquals(PaymentStatus.SUCCEEDED, updatedAttempt.getStatus());
        assertEquals("MOCK-GATEWAY-REF", updatedAttempt.getGatewayReference());

        assertEquals(
                OrderStatus.PAID,
                orders.findById(attempt.getOrder().getId()).orElseThrow().getStatus()
        );

        assertTrue(processedEvents.existsByEventId("event-success-1"));
    }

    @Test
    void lateSuccessWebhookAfterExpiryRequiresReconciliationAndDoesNotConsumeReleasedStock()
            throws Exception {

        PaymentAttempt attempt = initiatePayment(PaymentMethod.PAYTM);
        Long orderId = attempt.getOrder().getId();

        // Expire the reservation: checkout reserved 2 of the original 5 books.
        var order = orders.findById(orderId).orElseThrow();
        order.setExpiresAt(java.time.LocalDateTime.now(clock).minusMinutes(1));
        orders.saveAndFlush(order);
        payments.expireOrder(orderId);

        assertEquals(OrderStatus.EXPIRED, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(5, books.findById(book.getId()).orElseThrow().getStockQuantity());

        String body = json.writeValueAsString(successEvent(attempt, "event-late-success"));
        postWebhook(body, sign(body), 200);

        assertEquals(PaymentStatus.SUCCEEDED,
                paymentAttempts.findByPaymentIntentId(attempt.getPaymentIntentId())
                        .orElseThrow().getStatus());
        assertEquals(OrderStatus.REQUIRES_RECONCILIATION,
                orders.findById(orderId).orElseThrow().getStatus());

        // The late payment must not silently consume stock that was released to other orders.
        assertEquals(5, books.findById(book.getId()).orElseThrow().getStockQuantity());
        assertTrue(processedEvents.existsByEventId("event-late-success"));
    }

    @Test
    void expiryAndLateSuccessWebhookRaceReleasesStockOnlyOnce() throws Exception {

        PaymentAttempt attempt = initiatePayment(PaymentMethod.PAYTM);
        Long orderId = attempt.getOrder().getId();

        var order = orders.findById(orderId).orElseThrow();
        order.setExpiresAt(java.time.LocalDateTime.now(clock).minusMinutes(1));
        orders.saveAndFlush(order);

        String body = json.writeValueAsString(
                successEvent(attempt, "event-expiry-webhook-race"));
        String signature = sign(body);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> expiry = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Race did not start in time");
                }
                payments.expireOrder(orderId);
                return null;
            });

            Future<?> webhook = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Race did not start in time");
                }
                postWebhook(body, signature, 200);
                return null;
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS),
                    "Both concurrent operations should be ready");
            start.countDown();
            expiry.get(15, TimeUnit.SECONDS);
            webhook.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(OrderStatus.REQUIRES_RECONCILIATION,
                orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.SUCCEEDED,
                paymentAttempts.findByPaymentIntentId(attempt.getPaymentIntentId())
                        .orElseThrow().getStatus());
        assertEquals(5, books.findById(book.getId()).orElseThrow().getStockQuantity(),
                "The reservation must be released exactly once");
        assertTrue(processedEvents.existsByEventId("event-expiry-webhook-race"));
    }

    @Test
    void failedWebhookMarksPaymentAttemptFailedAndKeepsOrderPending() throws Exception {

        PaymentAttempt attempt = initiatePayment(PaymentMethod.PAYTM);
        Long orderId = attempt.getOrder().getId();

        MockPaymentEvent event = new MockPaymentEvent(
                "event-payment-failed",
                "payment_intent.payment_failed",
                attempt.getPaymentIntentId(),
                attempt.getAmount().movePointRight(2).longValueExact(),
                attempt.getCurrency(),
                null,
                "Card was declined"
        );
        String body = json.writeValueAsString(event);

        postWebhook(body, sign(body), 200);

        assertEquals(PaymentStatus.FAILED,
                paymentAttempts.findByPaymentIntentId(attempt.getPaymentIntentId())
                        .orElseThrow().getStatus());
        assertEquals(OrderStatus.PENDING,
                orders.findById(orderId).orElseThrow().getStatus());

        // A failed attempt does not expire the checkout reservation.
        assertEquals(3, books.findById(book.getId()).orElseThrow().getStockQuantity());
        assertTrue(processedEvents.existsByEventId("event-payment-failed"));
    }

    @Test
    void expiryJobExpiresPendingOrderAndRestoresStockOnlyOnce() throws Exception {

        PaymentAttempt attempt = initiatePayment(PaymentMethod.PAYTM);
        Long orderId = attempt.getOrder().getId();

        var order = orders.findById(orderId).orElseThrow();
        order.setExpiresAt(java.time.LocalDateTime.now(clock).minusMinutes(1));
        orders.saveAndFlush(order);

        expiryJob.expirePendingOrders();

        assertEquals(OrderStatus.EXPIRED,
                orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.EXPIRED,
                paymentAttempts.findByPaymentIntentId(attempt.getPaymentIntentId())
                        .orElseThrow().getStatus());
        assertEquals(5, books.findById(book.getId()).orElseThrow().getStockQuantity());

        // A subsequent scan must not release the same reservation again.
        expiryJob.expirePendingOrders();
        assertEquals(5, books.findById(book.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void duplicateWebhookEventDoesNotProcessPaymentTwice() throws Exception {

        PaymentAttempt attempt = initiatePayment(PaymentMethod.PHONEPE);

        String body = json.writeValueAsString(
                successEvent(attempt, "event-duplicate-1"));

        String signature = sign(body);

        postWebhook(body, signature, 200);
        postWebhook(body, signature, 200);

        assertEquals(1, processedEvents.count());

        assertEquals(
                PaymentStatus.SUCCEEDED,
                paymentAttempts.findByPaymentIntentId(
                        attempt.getPaymentIntentId()).orElseThrow().getStatus()
        );

        assertEquals(
                OrderStatus.PAID,
                orders.findById(attempt.getOrder().getId()).orElseThrow().getStatus()
        );
    }

    @Test
    void webhookWithInvalidSignatureIsRejectedAndDoesNotChangePayment()
            throws Exception {

        PaymentAttempt attempt = initiatePayment(PaymentMethod.RAZORPAY);

        String body = json.writeValueAsString(
                successEvent(attempt, "event-invalid-signature"));

        postWebhook(body, "00", 400);

        assertEquals(
                PaymentStatus.PENDING,
                paymentAttempts.findByPaymentIntentId(
                        attempt.getPaymentIntentId()).orElseThrow().getStatus()
        );

        assertEquals(
                OrderStatus.PENDING,
                orders.findById(attempt.getOrder().getId()).orElseThrow().getStatus()
        );

        assertEquals(0, processedEvents.count());
    }

    @Test
    void customerCannotInitiatePaymentForAnotherCustomersOrder()
            throws Exception {

        long orderId = checkoutOrder();

        mvc.perform(post("/api/payments/orders/{orderId}/initiate", orderId)
                        .headers(headers -> headers.setBasicAuth(
                                OTHER_EMAIL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"PAYTM\"}"))
                .andExpect(status().isNotFound());

        assertEquals(0, paymentAttempts.count());
    }

    @Test
    void initiatingAgainWithSameProviderReusesExistingPaymentAttempt()
            throws Exception {

        // Checkout only once.
        long orderId = checkoutOrder();

        // Both requests target the same order.
        PaymentAttempt first = initiatePayment(orderId, PaymentMethod.PAYTM);
        PaymentAttempt second = initiatePayment(orderId, PaymentMethod.PAYTM);

        assertEquals(first.getPaymentIntentId(), second.getPaymentIntentId());
        assertEquals(first.getClientToken(), second.getClientToken());

        // No duplicate payment attempt should be created.
        assertEquals(1, paymentAttempts.count());
    }

    private PaymentAttempt initiatePayment(PaymentMethod provider)
            throws Exception {

        long orderId = checkoutOrder();

        return initiatePayment(orderId, provider);
    }

    private PaymentAttempt initiatePayment(
            long orderId,
            PaymentMethod provider) throws Exception {

        String response = mvc.perform(
                        post("/api/payments/orders/{orderId}/initiate", orderId)
                                .headers(headers -> headers.setBasicAuth(
                                        EMAIL, PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"provider\":\"" + provider.name() + "\"}")
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode result = json.readTree(response);

        String intentId = result.path("paymentIntentId").asText();

        assertFalse(intentId.isBlank());
        assertEquals(provider.name(), result.path("provider").asText());
        assertEquals("PENDING", result.path("status").asText());

        return paymentAttempts.findByPaymentIntentId(intentId)
                .orElseThrow();
    }

    private long checkoutOrder() throws Exception {

        String response = mvc.perform(
                        post("/api/orders/checkout")
                                .headers(headers -> headers.setBasicAuth(
                                        EMAIL, PASSWORD))
                                .header("Idempotency-Key",
                                        "checkout-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"shippingAddress\":\"12 Test Street, Chennai\",\"cartId\":"
                                        + cart.getId() + "}")
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(response).path("orderId").asLong();
    }

    private MockPaymentEvent successEvent(
            PaymentAttempt attempt,
            String eventId) {

        return new MockPaymentEvent(
                eventId,
                "payment_intent.succeeded",
                attempt.getPaymentIntentId(),
                attempt.getAmount().movePointRight(2).longValueExact(),
                attempt.getCurrency(),
                "MOCK-GATEWAY-REF",
                null
        );
    }

    private void postWebhook(
            String body,
            String signature,
            int expectedStatus) throws Exception {

        mvc.perform(
                        post("/api/payments/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Mock-Timestamp",
                                        TEST_NOW.getEpochSecond())
                                .header("X-Mock-Signature", signature)
                                .content(body)
                )
                .andExpect(status().is(expectedStatus));
    }

    private String sign(String body) throws Exception {

        String timestamp = String.valueOf(TEST_NOW.getEpochSecond());

        Mac mac = Mac.getInstance("HmacSHA256");

        mac.init(new SecretKeySpec(
                WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));

        byte[] signature = mac.doFinal(
                (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(signature);
    }
}
