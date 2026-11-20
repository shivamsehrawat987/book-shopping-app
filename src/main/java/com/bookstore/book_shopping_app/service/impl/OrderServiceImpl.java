
package com.bookstore.book_shopping_app.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.dto.response.CheckoutRequest;
import com.bookstore.book_shopping_app.dto.response.CheckoutResponse;
import com.bookstore.book_shopping_app.dto.response.OrderResponse;
import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.CartItem;
import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.exception.business.CartException;
import com.bookstore.book_shopping_app.exception.business.OrderException;
import com.bookstore.book_shopping_app.mapper.OrderMapper;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.CartRepository;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.service.OrderService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final BigDecimal MAX_ORDER_TOTAL =
            new BigDecimal("9999999999.99");

    private static final int RESERVATION_MINUTES = 15;

    private final CartRepository carts;
    private final BookRepository books;
    private final OrderRepository orders;
    private final OrderMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public CheckoutResponse checkout(
            String email,
            String idempotencyKey,
            CheckoutRequest request) {

        validateIdempotencyKey(idempotencyKey);

        Cart cart = findCustomerCart(email, request.cartId());

        Optional<CheckoutResponse> retry = findRetryResponse(
                cart.getUser().getId(),
                cart.getId(),
                idempotencyKey,
                request
        );

        if (retry.isPresent()) {
            return retry.get();
        }

        validateCartForCheckout(cart);

        Order order = createOrder(cart, idempotencyKey, request);

        orders.saveAndFlush(order);

        return response(order);
    }

    private Cart findCustomerCart(String email, Long cartId) {

        return carts.findLockedById(cartId)
                .filter(cart ->
                        cart.getUser().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new CartException(
                        BookstoreErrorConstants.CART_NOT_FOUND,
                        BookstoreMessageConstants.CART_NOT_FOUND_MESSAGE
                ));
    }

    private void validateIdempotencyKey(String key) {

        if (key == null
                || key.isBlank()
                || key.length() > BookstoreErrorConstants.MAX_IDEMPOTENCY_KEY_LENGTH) {

            throw new OrderException(
                    BookstoreErrorConstants.INVALID_IDEMPOTENCY_KEY,
                    BookstoreMessageConstants.INVALID_IDEMPOTENCY_KEY_MESSAGE
            );
        }
    }

    private Optional<CheckoutResponse> findRetryResponse(
            Long userId,
            Long cartId,
            String key,
            CheckoutRequest request) {

        return orders.findByUserIdAndCheckoutKey(userId, key)
                .map(order -> {

                    validateRetryRequest(order, cartId, request);

                    validatePendingCheckout(order);

                    return response(order);
                });
    }

    private void validateRetryRequest(
            Order order,
            Long cartId,
            CheckoutRequest request) {

        boolean differentCart = !cartId.equals(order.getCartId());

        boolean differentAddress = !request.shippingAddress()
                .trim()
                .equals(order.getShippingAddress());

        if (differentCart || differentAddress) {
            throw new OrderException(
                    BookstoreErrorConstants.CHECKOUT_CONFLICT,
                    BookstoreMessageConstants.IDEMPOTENCY_KEY_REUSED_MESSAGE
            );
        }
    }

    private void validatePendingCheckout(Order order) {

        boolean pending = order.getStatus() == OrderStatus.PENDING;

        boolean notExpired = order.getExpiresAt()
                .isAfter(LocalDateTime.now(clock));

        if (!pending || !notExpired) {
            throw new OrderException(
                    BookstoreErrorConstants.CHECKOUT_CONFLICT,
                    BookstoreMessageConstants.CHECKOUT_NO_LONGER_PENDING_MESSAGE
            );
        }
    }

    private void validateCartForCheckout(Cart cart) {

        if (cart.getItems().isEmpty()) {
            throw new CartException(
                    BookstoreErrorConstants.CART_EMPTY,
                    BookstoreMessageConstants.CART_EMPTY_MESSAGE
            );
        }

        if (orders.existsByCartIdAndStatus(
                cart.getId(),
                OrderStatus.PENDING)) {

            throw new OrderException(
                    BookstoreErrorConstants.CHECKOUT_CONFLICT,
                    BookstoreMessageConstants.CHECKOUT_ALREADY_PENDING_MESSAGE
            );
        }
    }

    private Order createOrder(
            Cart cart,
            String idempotencyKey,
            CheckoutRequest request) {

        LocalDateTime now = LocalDateTime.now(clock);

        Order order = new Order(
                cart.getUser(),
                request.shippingAddress().trim()
        );

        order.prepareCheckout(
                cart.getId(),
                idempotencyKey,
                now.plusMinutes(RESERVATION_MINUTES)
        );

        reserveCartItems(order, cart);

        validateOrderTotal(order.getTotalAmount());

        return order;
    }

    private void reserveCartItems(Order order, Cart cart) {

        var items = cart.getItems()
                .stream()
                .sorted(Comparator.comparing(
                        item -> item.getBook().getId()))
                .toList();

        for (CartItem item : items) {
            reserveCartItem(order, item);
        }
    }

    private void reserveCartItem(Order order, CartItem item) {

        Book book = findBookForReservation(item.getBook().getId());

        validateAvailableStock(book, item.getQuantity());

        book.updateStock(
                book.getStockQuantity() - item.getQuantity()
        );

        order.addItem(book, item.getQuantity());
    }

    private Book findBookForReservation(Long bookId) {

        return books.findLockedById(bookId)
                .orElseThrow(() -> new OrderException(
                        BookstoreErrorConstants.CHECKOUT_CONFLICT,
                        BookstoreMessageConstants.BOOK_UNAVAILABLE_FOR_CHECKOUT_MESSAGE
                ));
    }

    private void validateAvailableStock(Book book, int requestedQuantity) {

        int availableStock = book.getStockQuantity();

        if (requestedQuantity <= 0
                || availableStock < requestedQuantity) {

            throw new CartException(
                    BookstoreErrorConstants.INSUFFICIENT_STOCK,
                    String.format(
                            BookstoreMessageConstants.INSUFFICIENT_BOOK_STOCK_MESSAGE,
                            availableStock,
                            book.getTitle(),
                            requestedQuantity
                    )
            );
        }
    }

    private void validateOrderTotal(BigDecimal total) {

        if (total.signum() <= 0
                || total.compareTo(MAX_ORDER_TOTAL) > 0) {

            throw new OrderException(
                    BookstoreErrorConstants.ORDER_TOTAL_UNSUPPORTED,
                    BookstoreMessageConstants.ORDER_TOTAL_UNSUPPORTED_MESSAGE
            );
        }
    }

    private CheckoutResponse response(Order order) {

        return new CheckoutResponse(
                order.getId(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getExpiresAt(),
                true
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String email, Long id) {

        return orders.findByIdAndUserEmailIgnoreCase(id, email)
                .map(mapper::toResponse)
                .orElseThrow(() -> new OrderException(
                        BookstoreErrorConstants.ORDER_NOT_FOUND,
                        BookstoreMessageConstants.ORDER_NOT_FOUND_MESSAGE
                ));
    }
}
