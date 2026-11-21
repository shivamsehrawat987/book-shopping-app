package com.bookstore.book_shopping_app.mapper;

import com.bookstore.book_shopping_app.dto.request.BookRequest;
import com.bookstore.book_shopping_app.dto.response.BookResponse;
import com.bookstore.book_shopping_app.entity.Book;

import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface BookMapper {

    BookResponse toResponse(Book book);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "title", expression = "java(request.title().trim())")
    @Mapping(target = "author", expression = "java(request.author().trim())")
    @Mapping(
            target = "description",
            expression = "java(request.description() == null || request.description().isBlank() ? null : request.description().trim())"
    )
    @Mapping(target = "version", ignore = true)
    Book toEntity(BookRequest request);
}