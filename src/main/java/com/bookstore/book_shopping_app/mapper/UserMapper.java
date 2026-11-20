package com.bookstore.book_shopping_app.mapper;

import com.bookstore.book_shopping_app.dto.request.RegisterRequest;
import com.bookstore.book_shopping_app.dto.response.UserResponse;
import com.bookstore.book_shopping_app.entity.User;
import com.bookstore.book_shopping_app.enums.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "email", expression = "java(request.email().trim().toLowerCase(java.util.Locale.ROOT))")
    @Mapping(target = "fullName", expression = "java(request.fullName().trim())")
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "role", source = "role")
    User toEntity(RegisterRequest request, String passwordHash, Role role);
}