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

class BookCatalogIntegrationTests extends BookstoreIntegrationTestSupport {

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
                .andExpect(jsonPath("$.pagination.hasNext", is(false)));

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
    void getBookByIdReturnsBookDetails() throws Exception {
        Book book = books.saveAndFlush(new Book(
                "Clean Code", "Robert Martin", new BigDecimal("450.00"),
                7, "A software craftsmanship book", BookCategory.TECHNOLOGY));

        mockMvc.perform(get("/api/books/{id}", book.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(book.getId().intValue())))
                .andExpect(jsonPath("$.title", is("Clean Code")))
                .andExpect(jsonPath("$.author", is("Robert Martin")))
                .andExpect(jsonPath("$.price").value(450.00))
                .andExpect(jsonPath("$.stockQuantity", is(7)))
                .andExpect(jsonPath("$.active", is(true)));
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
}
