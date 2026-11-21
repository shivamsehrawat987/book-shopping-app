
package com.bookstore.book_shopping_app.repository;

import java.util.ArrayList;
import java.util.List;

import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.enums.BookCategory;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class BookSpecifications {

    private BookSpecifications() {
    }

    public static Specification<Book> availableBooks(
            BookCategory category,
            String search
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> filters = new ArrayList<>();

            filters.add(criteriaBuilder.isTrue(root.get("active")));
            filters.add(criteriaBuilder.greaterThan(root.get("stockQuantity"), 0));

            if (category != null) {
                filters.add(criteriaBuilder.equal(root.get("category"), category));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";

                filters.add(criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("title")),
                                pattern
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("author")),
                                pattern
                        )
                ));
            }

            return criteriaBuilder.and(filters.toArray(Predicate[]::new));
        };
    }
}
