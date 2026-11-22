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

class ApiErrorHandlingIntegrationTests extends BookstoreIntegrationTestSupport {

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
}
