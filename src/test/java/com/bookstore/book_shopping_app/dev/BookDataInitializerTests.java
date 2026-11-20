package com.bookstore.book_shopping_app.dev;

import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:book-seed-tests;DB_CLOSE_DELAY=-1",
        "app.admin.bootstrap.enabled=false",
        "app.payment.mock.webhook-secret=book-seed-tests-only-secret-at-least-32-bytes",
        "app.checkout.expiry-scan-ms=3600000"
})
@ActiveProfiles("dev")
class BookDataInitializerTests {
    @Autowired private BookDataInitializer initializer;
    @Autowired private BookRepository books;

    @Test
    @Transactional
    void seedsValidBooksAndDoesNotResetExistingStock() throws Exception {
        assertEquals(4, books.count());
        var book = books.findByTitleIgnoreCaseAndAuthorIgnoreCase("THE CHECKOUT BOOK", "demo author").orElseThrow();
        book.updateStock(2);
        books.saveAndFlush(book);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(4, books.count());
        assertEquals(2, books.findById(book.getId()).orElseThrow().getStockQuantity());
        assertEquals(3, books.findAvailableBooks(null, null, PageRequest.of(0, 20)).getTotalElements());
        assertTrue(books.findAvailableBooks(BookCategory.TECHNOLOGY, null, PageRequest.of(0, 20))
                .getContent().stream().anyMatch(value -> value.getTitle().equals("Practical Spring")));
    }
}