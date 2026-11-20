
package com.bookstore.book_shopping_app.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.exception.business.CommonException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private final ApiProblemWriter problems;

    @ExceptionHandler(CommonException.class)
    public ProblemDetail handleCommon(CommonException exception) {

        HttpStatus status = switch (exception.getCode()) {

            case BookstoreErrorConstants.BOOK_NOT_FOUND,
                 BookstoreErrorConstants.CART_NOT_FOUND,
                 BookstoreErrorConstants.CART_ITEM_NOT_FOUND,
                 BookstoreErrorConstants.ORDER_NOT_FOUND,
                 BookstoreErrorConstants.PAYMENT_INTENT_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;

            case BookstoreErrorConstants.INVALID_CREDENTIALS ->
                    HttpStatus.UNAUTHORIZED;

            case BookstoreErrorConstants.ACCOUNT_ALREADY_EXISTS,
                 BookstoreErrorConstants.CART_PENDING_CHECKOUT,
                 BookstoreErrorConstants.INSUFFICIENT_STOCK,
                 BookstoreErrorConstants.CHECKOUT_CONFLICT,
                 BookstoreErrorConstants.PAYMENT_CONFLICT ->
                    HttpStatus.CONFLICT;

            case BookstoreErrorConstants.CART_EMPTY,
                 BookstoreErrorConstants.INVALID_IDEMPOTENCY_KEY,
                 BookstoreErrorConstants.INVALID_WEBHOOK ->
                    HttpStatus.BAD_REQUEST;

            case BookstoreErrorConstants.ORDER_TOTAL_UNSUPPORTED ->
                    HttpStatus.UNPROCESSABLE_ENTITY;

            case BookstoreErrorConstants.PAYMENT_GATEWAY_UNAVAILABLE ->
                    HttpStatus.BAD_GATEWAY;

            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {

            log.error(
                    "Unrecognized business exception code: {}",
                    exception.getCode(),
                    exception
            );

            return problems.create(
                    status,
                    BookstoreErrorConstants.INTERNAL_ERROR,
                    BookstoreMessageConstants.INTERNAL_ERROR_MESSAGE
            );
        }

        return problems.create(
                status,
                exception.getCode(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(
            ResponseStatusException exception) {

        return problems.create(
                exception.getStatusCode(),
                BookstoreErrorConstants.REQUEST_REJECTED,
                BookstoreMessageConstants.REQUEST_REJECTED_MESSAGE
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage()
            );
        }

        ProblemDetail problem = problems.create(
                HttpStatus.BAD_REQUEST,
                BookstoreErrorConstants.VALIDATION_FAILED,
                BookstoreMessageConstants.VALIDATION_FAILED_MESSAGE
        );

        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleInvalidParameters(
            HandlerMethodValidationException exception) {

        return problems.create(
                HttpStatus.BAD_REQUEST,
                BookstoreErrorConstants.VALIDATION_FAILED,
                BookstoreMessageConstants.VALIDATION_FAILED_MESSAGE
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception) {

        return problems.create(
                HttpStatus.BAD_REQUEST,
                BookstoreErrorConstants.VALIDATION_FAILED,
                BookstoreMessageConstants.VALIDATION_FAILED_MESSAGE
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(
            MissingRequestHeaderException exception) {

        return problems.create(
                HttpStatus.BAD_REQUEST,
                BookstoreErrorConstants.MISSING_HEADER,
                BookstoreMessageConstants.MISSING_HEADER_MESSAGE
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {

        return problems.create(
                HttpStatus.BAD_REQUEST,
                BookstoreErrorConstants.INVALID_PARAMETER,
                BookstoreMessageConstants.INVALID_PARAMETER_MESSAGE
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(
            HttpMessageNotReadableException exception) {

        return problems.create(
                HttpStatus.BAD_REQUEST,
                BookstoreErrorConstants.INVALID_REQUEST_BODY,
                BookstoreMessageConstants.INVALID_REQUEST_BODY_MESSAGE
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataConflict(
            DataIntegrityViolationException exception) {

        log.warn("Database integrity violation", exception);

        return problems.create(
                HttpStatus.CONFLICT,
                BookstoreErrorConstants.DATA_CONFLICT,
                BookstoreMessageConstants.DATA_CONFLICT_MESSAGE
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception) {

        if (exception instanceof ErrorResponse error
                && error.getStatusCode().is4xxClientError()) {

            ProblemDetail problem = problems.create(
                    error.getStatusCode(),
                    BookstoreErrorConstants.REQUEST_REJECTED,
                    BookstoreMessageConstants.REQUEST_REJECTED_MESSAGE
            );

            return ResponseEntity
                    .status(error.getStatusCode())
                    .headers(error.getHeaders())
                    .body(problem);
        }

        log.error("Unhandled API exception", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problems.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        BookstoreErrorConstants.INTERNAL_ERROR,
                        BookstoreMessageConstants.INTERNAL_ERROR_MESSAGE
                ));
    }
}
