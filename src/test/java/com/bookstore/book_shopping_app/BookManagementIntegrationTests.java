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

class BookManagementIntegrationTests extends BookstoreIntegrationTestSupport {

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
                .andExpect(jsonPath("$.version").value(0));
    }

@Test
    void adminCanUpdateBookAndCustomerCannot() throws Exception {
        Book book = books.saveAndFlush(new Book(
                "Old Title", "Old Author", new BigDecimal("100.00"),
                5, "Old description", BookCategory.FICTION));
        createCustomer();

        String updateRequest = updateBookRequest(book.getVersion());

        mockMvc.perform(put("/api/books/{id}", book.getId())
                        .headers(headers -> headers.setBasicAuth("reader@example.test", "ReaderPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/books/{id}", book.getId())
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.author", is("Updated Author")))
                .andExpect(jsonPath("$.active", is(true)));

        Book updated = books.findById(book.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Updated Title", updated.getTitle());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new BigDecimal("199.00").compareTo(updated.getPrice()));
    }

@Test
    void bookUpdateRejectsInvalidRequest() throws Exception {
        Book book = books.saveAndFlush(new Book(
                "Original", "Author", new BigDecimal("100.00"),
                3, null, BookCategory.FICTION));
        String invalidRequest = """
                {"title":" ","author":"Updated Author","price":199.00,
                 "description":"Description","category":"FICTION","version":%d}
                """.formatted(book.getVersion());

        mockMvc.perform(put("/api/books/{id}", book.getId())
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals("Original",
                books.findById(book.getId()).orElseThrow().getTitle());
    }

@Test
    void adminCanDeactivateBookAndCustomerCannot() throws Exception {
        Book book = books.saveAndFlush(new Book(
                "Active Book", "Author", new BigDecimal("120.00"),
                4, null, BookCategory.FICTION));
        createCustomer();

        mockMvc.perform(delete("/api/books/{id}", book.getId())
                        .headers(headers -> headers.setBasicAuth("reader@example.test", "ReaderPassword123!")))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertTrue(books.findById(book.getId()).orElseThrow().isActive());

        mockMvc.perform(delete("/api/books/{id}", book.getId())
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!")))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertFalse(books.findById(book.getId()).orElseThrow().isActive());
        mockMvc.perform(get("/api/books/{id}", book.getId()))
                .andExpect(status().isBadRequest());
    }

@Test
    void adminCanIncreaseAndDecreaseInventoryAndCustomerCannotAdjustIt() throws Exception {
        Book book = books.saveAndFlush(new Book(
                "Inventory Book", "Author", new BigDecimal("150.00"),
                5, null, BookCategory.TECHNOLOGY));
        createCustomer();

        mockMvc.perform(post("/api/books/{id}/inventory-adjustments", book.getId())
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3,\"type\":\"ADD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity", is(8)));

        mockMvc.perform(post("/api/books/{id}/inventory-adjustments", book.getId())
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2,\"type\":\"REMOVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity", is(6)));

        mockMvc.perform(post("/api/books/{id}/inventory-adjustments", book.getId())
                        .headers(headers -> headers.setBasicAuth("reader@example.test", "ReaderPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"type\":\"ADD\"}"))
                .andExpect(status().isForbidden());

        org.junit.jupiter.api.Assertions.assertEquals(6,
                books.findById(book.getId()).orElseThrow().getStockQuantity());
    }

@Test
    void inventoryAdjustmentRejectsZeroAndRemovalBeyondAvailableStock() throws Exception {
        Book book = books.saveAndFlush(new Book(
                "Limited Stock", "Author", new BigDecimal("150.00"),
                2, null, BookCategory.TECHNOLOGY));

        mockMvc.perform(post("/api/books/{id}/inventory-adjustments", book.getId())
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0,\"type\":\"ADD\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/books/{id}/inventory-adjustments", book.getId())
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3,\"type\":\"REMOVE\"}"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(2,
                books.findById(book.getId()).orElseThrow().getStockQuantity());
    }

@Test
    void updatingOrDeletingNonExistentBookReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/books/{id}", 999999L)
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBookRequest(0L)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/books/{id}", 999999L)
                        .headers(headers -> headers.setBasicAuth("admin@example.test", "AdminPassword123!")))
                .andExpect(status().isNotFound());
    }
}
