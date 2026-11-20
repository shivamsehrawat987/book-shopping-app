package com.bookstore.book_shopping_app;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.enums.Role;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.admin.bootstrap.enabled=false")
@AutoConfigureMockMvc
class ApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookRepository books;

    @Autowired
    private UserRepository users;

        @Autowired
        private PasswordEncoder passwordEncoder;

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

    @Test
        void registerAndBasicLoginReturnSafeUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reader@example.test","password":"ReaderPassword123!","fullName":"Reader One"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("reader@example.test")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/auth/login")
                        .headers(headers -> headers.setBasicAuth("READER@example.test", "ReaderPassword123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ROLE_CUSTOMER")))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User storedUser = users.findByEmailIgnoreCase("reader@example.test").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(
                passwordEncoder.matches("ReaderPassword123!", storedUser.getPasswordHash()));
    }

    @Test
    void bookListingFiltersAvailableBooksAndPaginates() throws Exception {
        books.save(new Book("Java Patterns", "A. Writer", new BigDecimal("20.00"), 4, null, BookCategory.TECHNOLOGY));
        books.save(new Book("Java Out of Stock", "B. Writer", new BigDecimal("15.00"), 0, null, BookCategory.TECHNOLOGY));
        books.save(new Book("Ocean Stories", "C. Writer", new BigDecimal("12.00"), 2, null, BookCategory.FICTION));

        mockMvc.perform(get("/api/books")
                        .param("category", "TECHNOLOGY")
                        .param("search", "java")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].title", is("Java Patterns")))
                .andExpect(jsonPath("$.pagination.page", is(0)))
                .andExpect(jsonPath("$.pagination.size", is(1)))
                .andExpect(jsonPath("$.pagination.totalElements", is(1)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)))
                .andExpect(jsonPath("$.pagination.hasNext", is(false)))
                .andExpect(jsonPath("$.data[0].version").doesNotExist());
    }

    @Test
    void bookListingCombinesOptionalFiltersAndPaginates() throws Exception {
        books.save(new Book("Alpha", "Jane Writer", new BigDecimal("20.00"), 2, null, BookCategory.TECHNOLOGY));
        books.save(new Book("Beta", "Other Author", new BigDecimal("25.00"), 1, null, BookCategory.TECHNOLOGY));
        books.save(new Book("Gamma", "Jane Writer", new BigDecimal("30.00"), 3, null, BookCategory.FICTION));
        books.save(new Book("Unavailable", "Jane Writer", new BigDecimal("10.00"), 0, null, BookCategory.TECHNOLOGY));

        mockMvc.perform(get("/api/books").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title", is("Alpha")))
                .andExpect(jsonPath("$.pagination.totalElements", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(3)))
                .andExpect(jsonPath("$.pagination.hasNext", is(true)));

        mockMvc.perform(get("/api/books").param("category", "TECHNOLOGY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements", is(2)));

        mockMvc.perform(get("/api/books").param("search", "  jAnE  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements", is(2)));

        mockMvc.perform(get("/api/books").param("category", "TECHNOLOGY").param("search", "jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title", is("Alpha")))
                .andExpect(jsonPath("$.pagination.totalElements", is(1)));

        mockMvc.perform(get("/api/books").param("search", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements", is(3)));
    }

    @Test
    void adminCanCreateBookAndCustomerCannot() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reader@example.test","password":"ReaderPassword123!","fullName":"Reader One"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/books")
                        .headers(headers -> headers.setBasicAuth("reader@example.test", "ReaderPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookRequest()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/books")
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("The Test Book")))
                .andExpect(jsonPath("$.category", is("FICTION")))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void onlyAdminCanCreateAnotherAdmin() throws Exception {
        String request = """
                {"email":"second-admin@example.test","password":"SecondAdminPass123!","fullName":"Second Admin"}
                """;

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reader@example.test","password":"ReaderPassword123!","fullName":"Reader One"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/users")
                        .headers(headers -> headers.setBasicAuth("reader@example.test", "ReaderPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users")
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("second-admin@example.test")))
                .andExpect(jsonPath("$.role", is("ROLE_ADMIN")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/auth/login")
                        .headers(headers -> headers.setBasicAuth("second-admin@example.test", "SecondAdminPass123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ROLE_ADMIN")));
    }

    @Test
    void missingBookReturnsNotFoundAndSwaggerSpecIsPublic() throws Exception {
        mockMvc.perform(get("/api/books/999"))
                .andExpect(status().isNotFound());

        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode openApi = objectMapper.readTree(spec);
        org.junit.jupiter.api.Assertions.assertTrue(openApi.path("paths").has("/api/books"));
        JsonNode bookOperations = openApi.path("paths").path("/api/books");
        org.junit.jupiter.api.Assertions.assertTrue(bookOperations.path("post").path("security").isArray());
        org.junit.jupiter.api.Assertions.assertFalse(bookOperations.path("get").has("security"));
        org.junit.jupiter.api.Assertions.assertEquals("basic",
                openApi.path("components").path("securitySchemes").path("basicAuth").path("scheme").asText());
    }

    @Test
    void basicAuthenticationRejectsMissingWrongAndBearerCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "WrongPassword123!")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .headers(headers -> headers.setBasicAuth("missing@example.test", "WrongPassword123!")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/books")
                        .header("Authorization", "Bearer obsolete-token")
                        .contentType(MediaType.APPLICATION_JSON).content(bookRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRegistrationAndDuplicateEmailAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":""}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ADMIN@example.test","password":"Password123456!","fullName":"Duplicate"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void apiErrorsHaveConsistentProblemDetails() throws Exception {
        mockMvc.perform(get("/api/books/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.detail", is("Book not found")))
                .andExpect(jsonPath("$.code", is("BOOK_NOT_FOUND")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_REQUEST_BODY")));

        mockMvc.perform(get("/api/books").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/books").param("category", "INVALID_CATEGORY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));

        mockMvc.perform(post("/api/orders/checkout")
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shippingAddress":"Test Street","cartId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MISSING_HEADER")));

        mockMvc.perform(put("/api/auth/login")
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code", is("REQUEST_REJECTED")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.TEXT_PLAIN).content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("REQUEST_REJECTED")));
    }

    private String bookRequest() {
        return """
                {"title":"The Test Book","author":"Test Author","price":19.95,"stockQuantity":3,"category":"FICTION"}
                """;
    }
}