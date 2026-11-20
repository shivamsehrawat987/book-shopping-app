package com.bookstore.book_shopping_app.mapper;

import java.math.BigDecimal;

import com.bookstore.book_shopping_app.dto.response.CartItemResponse;
import com.bookstore.book_shopping_app.dto.response.CartResponse;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CartMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "totalAmount", expression = "java(total(cart))")
    CartResponse toResponse(Cart cart);

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "bookTitle", source = "book.title")
    @Mapping(target = "unitPrice", source = "book.price")
    @Mapping(target = "lineTotal", expression = "java(item.getBook().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))")
    CartItemResponse toResponse(CartItem item);

    default BigDecimal total(Cart cart) {
        return cart.getItems().stream()
                .map(item -> item.getBook().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}