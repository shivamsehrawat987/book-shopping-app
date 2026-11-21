
package com.bookstore.book_shopping_app.service.impl;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.dto.request.BookRequest;
import com.bookstore.book_shopping_app.dto.request.BookUpdateRequest;
import com.bookstore.book_shopping_app.dto.request.InventoryAdjustmentRequest;
import com.bookstore.book_shopping_app.dto.response.BookResponse;
import com.bookstore.book_shopping_app.dto.response.PageResponse;
import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.exception.business.BookException;
import com.bookstore.book_shopping_app.mapper.BookMapper;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.service.BookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookServiceImpl implements BookService {

    private final BookRepository books;
    private final BookMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponse> list(
            BookCategory category,
            String search,
            int page,
            int size
    ) {
        String term = search == null || search.isBlank()
                ? null
                : search.trim();

        return PageResponse.from(
                books.findAvailableBooks(
                        category,
                        term,
                        PageRequest.of(page, size, Sort.by("id").ascending())
                ).map(mapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse getById(long id) {
        Book book = findBook(id);
        ensureActive(book);
        return mapper.toResponse(book);
    }

    @Override
    @Transactional
    public BookResponse create(BookRequest request) {
        Book book = mapper.toEntity(request);
        Book saved = books.save(book);
        log.info("Book created: bookId={}, category={}", saved.getId(), saved.getCategory());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BookResponse update(long id, BookUpdateRequest request) {
        Book book = findBook(id);
        ensureActive(book);

        if (!book.getVersion().equals(request.version())) {
            throw new BookException(
                    BookstoreErrorConstants.BOOK_VERSION_CONFLICT,
                    BookstoreMessageConstants.BOOK_VERSION_CONFLICT_MESSAGE
            );
        }

        book.updateDetails(
                request.title(),
                request.author(),
                request.price(),
                request.description(),
                request.category()
        );

        try {
            books.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new BookException(
                    BookstoreErrorConstants.BOOK_VERSION_CONFLICT,
                    BookstoreMessageConstants.BOOK_VERSION_CONFLICT_MESSAGE
            );
        }

        log.info("Book updated: bookId={}, version={}", book.getId(), book.getVersion());
        return mapper.toResponse(book);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        Book book = findBook(id);
        book.deactivate();
        log.info("Book deactivated: bookId={}", book.getId());
    }

    @Override
    @Transactional
    public BookResponse adjustInventory(
            long id,
            InventoryAdjustmentRequest request
    ) {
        Book book = books.findLockedById(id)
                .orElseThrow(this::bookNotFound);

        try {
            book.adjustStock(request.quantityChange());
        } catch (IllegalArgumentException exception) {
            throw new BookException(
                    BookstoreErrorConstants.INVALID_STOCK_ADJUSTMENT,
                    BookstoreMessageConstants.INVALID_STOCK_ADJUSTMENT_MESSAGE
            );
        }

        log.info("Book inventory adjusted: bookId={}, quantityChange={}, resultingStock={}",
                book.getId(), request.quantityChange(), book.getStockQuantity());
        return mapper.toResponse(book);
    }

    private Book findBook(long id) {
        return books.findById(id)
                .orElseThrow(this::bookNotFound);
    }

    private BookException bookNotFound() {
        return new BookException(
                BookstoreErrorConstants.BOOK_NOT_FOUND,
                BookstoreMessageConstants.BOOK_NOT_FOUND_MESSAGE
        );
    }

    private void ensureActive(Book book) {
        if (!book.isActive()) {
            throw new BookException(
                    BookstoreErrorConstants.BOOK_INACTIVE,
                    BookstoreMessageConstants.BOOK_INACTIVE_MESSAGE
            );
        }
    }
}
