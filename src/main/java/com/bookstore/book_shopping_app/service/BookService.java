
package com.bookstore.book_shopping_app.service;

import com.bookstore.book_shopping_app.dto.request.BookRequest;
import com.bookstore.book_shopping_app.dto.request.BookUpdateRequest;
import com.bookstore.book_shopping_app.dto.request.InventoryAdjustmentRequest;
import com.bookstore.book_shopping_app.dto.response.BookResponse;
import com.bookstore.book_shopping_app.dto.response.PageResponse;
import com.bookstore.book_shopping_app.enums.BookCategory;

public interface BookService {

    PageResponse<BookResponse> list(
            BookCategory category,
            String search,
            int page,
            int size
    );

    BookResponse getById(long id);

    BookResponse create(BookRequest request);

    BookResponse update(long id, BookUpdateRequest request);

    void deactivate(long id);

    BookResponse adjustInventory(
            long id,
            InventoryAdjustmentRequest request
    );
}
