package com.bookstore.book_shopping_app.repository;

import java.util.Optional;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.enums.BookCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

        Optional<Book> findByTitleIgnoreCaseAndAuthorIgnoreCase(String title, String author);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<Book> findLockedById(Long id);

        default Page<Book> findAvailableBooks(BookCategory category, String search, Pageable pageable) {
                return findAll(BookSpecifications.availableBooks(category, search), pageable);
        }
}