package com.bookstore.book_shopping_app;
import com.bookstore.book_shopping_app.entity.Book;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cart-api-tests;DB_CLOSE_DELAY=-1",
        "app.admin.bootstrap.enabled=false",
        "app.payment.mock.webhook-secret=cart-api-tests-secret-at-least-32-bytes",
        "app.checkout.expiry-scan-ms=3600000"
})
@AutoConfigureMockMvc
class CartApiIntegrationTests {
    private static final String EMAIL = "cart-reader@example.test";
    private static final String PASSWORD = "ReaderPassword123!";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private BookRepository books;
    @Autowired private CartRepository carts;
    @Autowired private OrderRepository orders;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private Clock clock;
    private Book book;
    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-19T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        orders.deleteAll();
        carts.deleteAll();
        books.deleteAll();
        users.deleteAll();
        users.save(new User(EMAIL, passwordEncoder.encode(PASSWORD), "Cart Reader"));
        book = books.saveAndFlush(new Book(
                "The Checkout Book", "Demo Author", new BigDecimal("299.00"),
                5, "A book for integration testing", BookCategory.FICTION
        ));
    }
    @Test
    void customerCanAddUpdateReadAndRemoveCartItemThroughApi() throws Exception {
        String cartJson = mvc.perform(post("/api/cart/items")
                        .headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + ",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode added = json.readTree(cartJson);
        assertEquals(1, added.path("items").size());
        assertEquals(2, added.at("/items/0/quantity").asInt());
        assertEquals(0, new BigDecimal("598.00").compareTo(
                added.path("totalAmount").decimalValue()));
        long itemId = added.at("/items/0/id").asLong();
        String updatedJson = mvc.perform(put("/api/cart/items/{itemId}", itemId)
                        .headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode updated = json.readTree(updatedJson);
        assertEquals(3, updated.at("/items/0/quantity").asInt());
        assertEquals(0, new BigDecimal("897.00").compareTo(
                updated.path("totalAmount").decimalValue()));
        mvc.perform(get("/api/cart").headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD)))
                .andExpect(status().isOk());
        String removedJson = mvc.perform(delete("/api/cart/items/{itemId}", itemId)
                        .headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode removed = json.readTree(removedJson);
        assertEquals(0, removed.path("items").size());
        assertEquals(0, BigDecimal.ZERO.compareTo(removed.path("totalAmount").decimalValue()));
    }
    @Test
    void customerCanClearCartAndCartTotalIsReset() throws Exception {
        String cartJson = mvc.perform(post("/api/cart/items")
                        .headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + ",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode populatedCart = json.readTree(cartJson);
        assertEquals(1, populatedCart.path("items").size());
        assertEquals(0, new BigDecimal("598.00").compareTo(
                populatedCart.path("totalAmount").decimalValue()));
        String clearedJson = mvc.perform(delete("/api/cart")
                        .headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode clearedCart = json.readTree(clearedJson);
        assertEquals(0, clearedCart.path("items").size());
        assertEquals(0, BigDecimal.ZERO.compareTo(clearedCart.path("totalAmount").decimalValue()));
        // Clearing the cart must not change the actual book inventory.
        assertEquals(5, books.findById(book.getId()).orElseThrow().getStockQuantity());
    }
    @Test
    void cartRejectsQuantityAboveAvailableStockWithoutChangingCart() throws Exception {
        mvc.perform(post("/api/cart/items")
                        .headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + ",\"quantity\":6}"))
                .andExpect(status().isConflict());
        String cartJson = mvc.perform(get("/api/cart").headers(headers -> headers.setBasicAuth(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode cart = json.readTree(cartJson);
        assertEquals(0, cart.path("items").size());
        assertEquals(5, books.findById(book.getId()).orElseThrow().getStockQuantity());
    }
}
