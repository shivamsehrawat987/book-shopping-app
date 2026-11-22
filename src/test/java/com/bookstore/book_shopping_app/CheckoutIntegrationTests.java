package com.bookstore.book_shopping_app;
import java.math.BigDecimal;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.CartItem;
import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.BookCategory;
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
        cart = Cart.builder().user(user).build();
        addCartItem(cart, book, 2);
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
    private void addCartItem(Cart cart, Book book, int quantity) {
        cart.getItems().add(CartItem.builder()
                .cart(cart)
                .book(book)
                .quantity(quantity)
                .build());
    }
}
