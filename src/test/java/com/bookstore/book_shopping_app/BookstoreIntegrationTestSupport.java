package com.bookstore.book_shopping_app;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.enums.Role;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.is;

@SpringBootTest(properties = "app.admin.bootstrap.enabled=false")
@AutoConfigureMockMvc
abstract class BookstoreIntegrationTestSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected BookRepository books;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        books.deleteAll();
        users.deleteAll();
        users.save(new User(
                "admin@example.test",
                passwordEncoder.encode("AdminPassword123!"),
                "Administrator",
                Role.ROLE_ADMIN
        ));
    }

    protected void createCustomer() {
        users.save(new User(
                "reader@example.test",
                passwordEncoder.encode("ReaderPassword123!"),
                "Reader One",
                Role.ROLE_CUSTOMER
        ));
    }

    protected String bookRequest() {
        return """
                {"title":"The Test Book","author":"Test Author","price":19.95,"stockQuantity":3,"category":"FICTION"}
                """;
    }

    protected String updateBookRequest(Long version) {
        return """
                {"title":"Updated Title","author":"Updated Author","price":199.00,
                 "description":"Updated description","category":"TECHNOLOGY","version":%d}
                """.formatted(version);
    }
}
