package com.bookstore.book_shopping_app.controller;

import java.net.URI;

import com.bookstore.book_shopping_app.dto.request.BookRequest;
import com.bookstore.book_shopping_app.dto.response.BookResponse;
import com.bookstore.book_shopping_app.dto.response.PageResponse;
import com.bookstore.book_shopping_app.enums.BookCategory;
import com.bookstore.book_shopping_app.service.BookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public PageResponse<BookResponse> list(
            @RequestParam(required = false) BookCategory category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return bookService.list(category, search, page, size);
    }

    @GetMapping("/{id}")
    public BookResponse getById(@PathVariable @Min(1) long id) {
        return bookService.getById(id);
    }

    @PostMapping
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity.created(URI.create("/api/books/" + created.id()))
                .body(created);
    }
}