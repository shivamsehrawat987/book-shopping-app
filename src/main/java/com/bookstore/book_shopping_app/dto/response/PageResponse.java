package com.bookstore.book_shopping_app.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(
    List<T> data,
    Pagination pagination
) {
    public PageResponse {
    data = List.copyOf(data);
    }

    public record Pagination(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
    ) {
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
        new Pagination(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext()
        )
        );
    }
}