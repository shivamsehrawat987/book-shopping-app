package com.bookstore.book_shopping_app;
import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.CartItem;
import com.bookstore.book_shopping_app.entity.PaymentAttempt;
import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.enums.PaymentStatus;
import com.bookstore.book_shopping_app.payment.MockPaymentEvent;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.CartRepository;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.repository.PaymentAttemptRepository;
import com.bookstore.book_shopping_app.repository.UserRepository;
import com.bookstore.book_shopping_app.repository.ProcessedPaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-integration-tests;DB_CLOSE_DELAY=-1",
        "app.admin.bootstrap.enabled=false",
        "app.payment.mock.webhook-secret=payment-integration-tests-secret-at-least-32-bytes",
        "app.checkout.expiry-scan-ms=3600000"
})
@AutoConfigureMockMvc
class PaymentIntegrationTests {
    private static final String EMAIL = "payment-reader@example.test";
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
    @Autowired private ProcessedPaymentEventRepository processedEvents;
    @Autowired private PaymentAttemptRepository paymentAttempts;
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
        users.flush();
        User customer = users.saveAndFlush(new User(
                EMAIL, passwordEncoder.encode(PASSWORD), "Payment Reader"));
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
        PaymentAttempt attempt = initiatePayment();
        postWebhook(successEvent(attempt, "event-success-1"), 200);
        PaymentAttempt updatedAttempt = paymentAttempts
                .findByPaymentIntentId(attempt.getPaymentIntentId())
                .orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, updatedAttempt.getStatus());
        assertEquals(
                OrderStatus.PAID,
                orders.findById(attempt.getOrder().getId()).orElseThrow().getStatus()
        );
    }
    @Test
    @Transactional
    void failedWebhookCancelsOrderRestoresStockAndKeepsCart() throws Exception {
        PaymentAttempt attempt = initiatePayment();
        MockPaymentEvent event = new MockPaymentEvent(
                "event-payment-failed",
                "payment_intent.payment_failed",
                attempt.getPaymentIntentId(),
                attempt.getAmount().movePointRight(2).longValueExact(),
                attempt.getCurrency(),
                null,
                "Card was declined"
        );
        postWebhook(event, 200);
        PaymentAttempt updatedAttempt = paymentAttempts
                .findByPaymentIntentId(attempt.getPaymentIntentId())
                .orElseThrow();
        assertEquals(PaymentStatus.FAILED, updatedAttempt.getStatus());
        assertEquals(
                OrderStatus.CANCELLED,
                orders.findById(attempt.getOrder().getId()).orElseThrow().getStatus()
        );
        assertEquals(5, books.findById(book.getId()).orElseThrow().getStockQuantity());
        Cart retainedCart = carts.findById(cart.getId()).orElseThrow();
        assertEquals(1, retainedCart.getItems().size());
        assertEquals(2, retainedCart.getItems().getFirst().getQuantity());
    }
    private PaymentAttempt initiatePayment() throws Exception {
        long orderId = checkoutOrder();
        String response = mvc.perform(
                        post("/api/payments/orders/{orderId}/initiate", orderId)
                                .headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"provider\":\"PAYTM\"}")
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode result = json.readTree(response);
        String intentId = result.path("paymentIntentId").asText();
        assertFalse(intentId.isBlank());
        assertEquals("PAYTM", result.path("provider").asText());
        assertEquals("PENDING", result.path("status").asText());
        return paymentAttempts.findByPaymentIntentId(intentId).orElseThrow();
    }
    private long checkoutOrder() throws Exception {
        String response = mvc.perform(
                        post("/api/orders/checkout")
                                .headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD))
                                .header("Idempotency-Key", "checkout-" + UUID.randomUUID())
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
    private MockPaymentEvent successEvent(PaymentAttempt attempt, String eventId) {
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
    private void postWebhook(MockPaymentEvent event, int expectedStatus) throws Exception {
        String body = json.writeValueAsString(event);
        String timestamp = String.valueOf(TEST_NOW.getEpochSecond());
        mvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Mock-Timestamp", timestamp)
                        .header("X-Mock-Signature", sign(timestamp + "." + body))
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }
    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        return HexFormat.of().formatHex(
                mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
