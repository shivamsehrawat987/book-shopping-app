package com.bookstore.book_shopping_app.mapper;

import com.bookstore.book_shopping_app.dto.response.OrderItemResponse;
import com.bookstore.book_shopping_app.dto.response.OrderResponse;
import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {

    @Mapping(target = "userId", source = "user.id")
    OrderResponse toResponse(Order order);

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "lineTotal", expression = "java(item.getPriceAtPurchase().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    OrderItemResponse toResponse(OrderItem item);
}