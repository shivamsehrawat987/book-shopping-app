package com.bookstore.book_shopping_app.service.impl;

import com.bookstore.book_shopping_app.dto.request.BookRequest;
import com.bookstore.book_shopping_app.dto.response.BookResponse;
import com.bookstore.book_shopping_app.dto.response.PageResponse;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.exception.business.BookException;
import com.bookstore.book_shopping_app.mapper.BookMapper;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository books;
    private final BookMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponse> list(BookCategory category, String search, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(books.findAvailableBooks(category, term,
                        PageRequest.of(page, size, Sort.by("id").ascending().and(Sort.by("id"))))
                .map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse getById(long id) {
        return books.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new BookException("BOOK_NOT_FOUND", "Book not found"));
    }

    @Override
    @Transactional
    public BookResponse create(BookRequest request) {
        return mapper.toResponse(books.save(mapper.toEntity(request)));
    }
}