
package com.bookstore.book_shopping_app;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.CartRepository;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.repository.UserRepository;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkout-tests;DB_CLOSE_DELAY=-1",
        "app.admin.bootstrap.enabled=false",
        "app.checkout.expiry-scan-ms=3600000"
})
@AutoConfigureMockMvc
class CheckoutIntegrationTests {

    private static final String PASSWORD = "ReaderPassword123!";

    private static final Instant START =
            Instant.parse("2026-11-11T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private BookRepository books;

    @Autowired
    private UserRepository users;

    @Autowired
    private CartRepository carts;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private Clock clock;

    private Book book;

    private Cart cart;

    @BeforeEach
    void setup() {

        org.mockito.Mockito.when(clock.instant()).thenReturn(START);
        org.mockito.Mockito.when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        orders.deleteAll();
        carts.deleteAll();
        books.deleteAll();
        users.deleteAll();

        User user = users.save(
                new User(
                        "reader@example.test",
                        encoder.encode(PASSWORD),
                        "Reader"
                )
        );

        users.save(
                new User(
                        "other@example.test",
                        encoder.encode(PASSWORD),
                        "Other"
                )
        );

        book = books.save(
                new Book(
                        "Checkout Book",
                        "Writer",
                        new BigDecimal("20.00"),
                        5,
                        null,
                        BookCategory.FICTION
                )
        );

        cart = new Cart(user);
        cart.addItem(book, 2);

        carts.saveAndFlush(cart);
    }

    @Test
    void checkoutCreatesOrderAndReservesStock() throws Exception {

        JsonNode checkout = checkout(cart.getId(), "checkout-1");

        long orderId = checkout.path("orderId").asLong();

        assertTrue(orderId > 0);

        assertEquals(
                new BigDecimal("40.00"),
                orders.findById(orderId)
                        .orElseThrow()
                        .getTotalAmount()
        );

        assertEquals(
                OrderStatus.PENDING,
                orders.findById(orderId)
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                "INR",
                checkout.path("currency").asText()
        );

        assertTrue(checkout.path("paymentRequired").asBoolean());

        assertEquals(
                3,
                books.findById(book.getId())
                        .orElseThrow()
                        .getStockQuantity()
        );
    }

    @Test
    void duplicateCheckoutDoesNotCreateAnotherOrder() throws Exception {

        JsonNode first = checkout(cart.getId(), "checkout-duplicate");

        JsonNode second = checkout(cart.getId(), "checkout-duplicate");

        assertEquals(
                first.path("orderId").asLong(),
                second.path("orderId").asLong()
        );

        assertEquals(1, orders.count());

        assertEquals(
                3,
                books.findById(book.getId())
                        .orElseThrow()
                        .getStockQuantity()
        );
    }

    @Test
    void checkoutRejectsAnotherUsersCart() throws Exception {

        mvc.perform(
                post("/api/orders/checkout")
                        .headers(headers ->
                                headers.setBasicAuth(
                                        "other@example.test",
                                        PASSWORD
                                )
                        )
                        .header("Idempotency-Key", "wrong-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(cart.getId()))
        ).andExpect(status().isNotFound());

        assertEquals(0, orders.count());

        assertEquals(
                5,
                books.findById(book.getId())
                        .orElseThrow()
                        .getStockQuantity()
        );
    }

    @Test
    void checkoutRejectsEmptyCart() throws Exception {

        Cart emptyCart = carts.saveAndFlush(
                new Cart(
                        users.findByEmailIgnoreCase("other@example.test")
                                .orElseThrow()
                )
        );

        mvc.perform(
                post("/api/orders/checkout")
                        .headers(headers ->
                                headers.setBasicAuth(
                                        "other@example.test",
                                        PASSWORD
                                )
                        )
                        .header("Idempotency-Key", "empty-cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(emptyCart.getId()))
        ).andExpect(status().isBadRequest());

        assertEquals(0, orders.count());
    }

    @Test
    void checkoutRejectsInsufficientStock() throws Exception {

        book.updateStock(1);
        books.saveAndFlush(book);

        checkoutStatus(cart.getId(), "no-stock", 409);

        assertEquals(0, orders.count());

        assertEquals(
                1,
                books.findById(book.getId())
                        .orElseThrow()
                        .getStockQuantity()
        );
    }

    @Test
    void checkoutRequiresAuthentication() throws Exception {

        mvc.perform(
                post("/api/orders/checkout")
                        .header("Idempotency-Key", "anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(cart.getId()))
        ).andExpect(status().isUnauthorized());

        assertEquals(0, orders.count());
    }

    @Test
    void checkoutValidatesRequest() throws Exception {

        mvc.perform(
                post("/api/orders/checkout")
                        .headers(headers ->
                                headers.setBasicAuth(
                                        "reader@example.test",
                                        PASSWORD
                                )
                        )
                        .header("Idempotency-Key", "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"shippingAddress\":\"\",\"cartId\":-1}"
                        )
        ).andExpect(status().isBadRequest());

        assertEquals(0, orders.count());
    }

    @Test
    void checkoutRejectsMissingIdempotencyKey() throws Exception {

        mvc.perform(
                post("/api/orders/checkout")
                        .headers(headers ->
                                headers.setBasicAuth(
                                        "reader@example.test",
                                        PASSWORD
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(cart.getId()))
        ).andExpect(status().isBadRequest());

        assertEquals(0, orders.count());
    }

    @Test
    void checkoutRejectsDifferentDetailsForSameIdempotencyKey()
            throws Exception {

        checkout(cart.getId(), "same-key");

        mvc.perform(
                post("/api/orders/checkout")
                        .headers(headers ->
                                headers.setBasicAuth(
                                        "reader@example.test",
                                        PASSWORD
                                )
                        )
                        .header("Idempotency-Key", "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of(
                                                "shippingAddress",
                                                "Different address",
                                                "cartId",
                                                cart.getId()
                                        )
                                )
                        )
        ).andExpect(status().isConflict());

        assertEquals(1, orders.count());
    }

    @Test
    void competingCheckoutsCannotOversell() throws Exception {

        book.updateStock(2);
        books.saveAndFlush(book);

        Cart secondCart = new Cart(
                users.findByEmailIgnoreCase("other@example.test")
                        .orElseThrow()
        );

        secondCart.addItem(book, 2);
        carts.saveAndFlush(secondCart);

        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {

            var first = executor.submit(() -> {
                start.await();
                return checkoutCode(
                        "reader@example.test",
                        cart.getId(),
                        "race-1"
                );
            });

            var second = executor.submit(() -> {
                start.await();
                return checkoutCode(
                        "other@example.test",
                        secondCart.getId(),
                        "race-2"
                );
            });

            start.countDown();

            List<Integer> results = List.of(
                    first.get(),
                    second.get()
            );

            assertTrue(results.contains(201));
            assertTrue(results.contains(409));
        }

        assertEquals(1, orders.count());

        assertEquals(
                0,
                books.findById(book.getId())
                        .orElseThrow()
                        .getStockQuantity()
        );
    }

    @Test
    void partialReservationRollsBackWhenAnotherBookHasNoStock()
            throws Exception {

        Book unavailable = books.save(
                new Book(
                        "Unavailable Book",
                        "Writer",
                        new BigDecimal("10.00"),
                        0,
                        null,
                        BookCategory.FICTION
                )
        );

        carts.deleteAll();

        Cart replacement = new Cart(
                users.findByEmailIgnoreCase("reader@example.test")
                        .orElseThrow()
        );

        replacement.addItem(book, 2);
        replacement.addItem(unavailable, 1);

        carts.saveAndFlush(replacement);

        checkoutStatus(replacement.getId(), "partial", 409);

        assertEquals(0, orders.count());

        assertEquals(
                5,
                books.findById(book.getId())
                        .orElseThrow()
                        .getStockQuantity()
        );
    }

    @Test
    void orderIsAccessibleOnlyToItsOwner() throws Exception {

        JsonNode checkout = checkout(cart.getId(), "owned");

        long orderId = checkout.path("orderId").asLong();

        mvc.perform(
                get("/api/orders/" + orderId)
                        .headers(headers ->
                                headers.setBasicAuth(
                                        "other@example.test",
                                        PASSWORD
                                )
                        )
        ).andExpect(status().isNotFound());

        mvc.perform(
                get("/api/orders/" + orderId)
                        .headers(headers ->
                                headers.setBasicAuth(
                                        "reader@example.test",
                                        PASSWORD
                                )
                        )
        ).andExpect(status().isOk());
    }

    private JsonNode checkout(Long cartId, String key) throws Exception {

        String body = mvc.perform(
                        post("/api/orders/checkout")
                                .headers(headers ->
                                        headers.setBasicAuth(
                                                "reader@example.test",
                                                PASSWORD
                                        )
                                )
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request(cartId))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(body);
    }

    private String request(Long cartId) throws Exception {

        return json.writeValueAsString(
                Map.of(
                        "shippingAddress",
                        "12 Test Street, Mumbai",
                        "cartId",
                        cartId
                )
        );
    }

    private void checkoutStatus(
            Long cartId,
            String key,
            int expectedStatus
    ) throws Exception {

        assertEquals(
                expectedStatus,
                checkoutCode(
                        "reader@example.test",
                        cartId,
                        key
                )
        );
    }

    private int checkoutCode(
            String email,
            Long cartId,
            String key
    ) throws Exception {

        return mvc.perform(
                        post("/api/orders/checkout")
                                .headers(headers ->
                                        headers.setBasicAuth(email, PASSWORD)
                                )
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request(cartId))
                )
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
