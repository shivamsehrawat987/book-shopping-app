package com.bookstore.book_shopping_app;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.enums.Role;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationIntegrationTests extends BookstoreIntegrationTestSupport {

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
}
