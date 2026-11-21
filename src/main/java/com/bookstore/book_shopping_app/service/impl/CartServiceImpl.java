
package com.bookstore.book_shopping_app.service.impl;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.dto.request.CartItemQuantityRequest;
import com.bookstore.book_shopping_app.dto.request.CartItemRequest;
import com.bookstore.book_shopping_app.dto.response.CartResponse;
import com.bookstore.book_shopping_app.entity.Book;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.CartItem;
import com.bookstore.book_shopping_app.enums.OrderStatus;
import com.bookstore.book_shopping_app.exception.business.AuthException;
import com.bookstore.book_shopping_app.exception.business.BookException;
import com.bookstore.book_shopping_app.exception.business.CartException;
import com.bookstore.book_shopping_app.mapper.CartMapper;
import com.bookstore.book_shopping_app.repository.BookRepository;
import com.bookstore.book_shopping_app.repository.CartRepository;
import com.bookstore.book_shopping_app.repository.OrderRepository;
import com.bookstore.book_shopping_app.repository.UserRepository;
import com.bookstore.book_shopping_app.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository carts;
    private final BookRepository books;
    private final OrderRepository orders;
    private final UserRepository users;
    private final CartMapper mapper;

    @Override
    @Transactional
    public CartResponse getCart(String email) {

        return carts.findByUserEmailIgnoreCase(email)
                .map(mapper::toResponse)
                .orElseGet(() -> createCart(email));
    }

    @Override
    @Transactional
    public CartResponse addItem(String email, CartItemRequest request) {

        Cart cart = findOrCreateLockedCart(email);

        ensureCartCanBeModified(cart.getId());

        Book book = findBook(request.bookId());

        validateAvailableStock(cart, book, request.quantity());

        addOrIncreaseCartItem(cart, book, request.quantity());
        log.info("Cart item added: cartId={}, bookId={}, quantity={}",
                cart.getId(), book.getId(), request.quantity());

        carts.flush();

        return mapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItemQuantity(
            String email,
            Long itemId,
            CartItemQuantityRequest request) {

        Cart cart = findLockedCart(email);

        ensureCartCanBeModified(cart.getId());

        CartItem item = findCartItem(cart, itemId);

        validateUpdatedQuantity(item, request.quantity());

        item.setQuantity(request.quantity());
        log.info("Cart item quantity updated: cartId={}, cartItemId={}, quantity={}",
                cart.getId(), item.getId(), request.quantity());

        return mapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(String email, Long itemId) {

        Cart cart = findLockedCart(email);

        ensureCartCanBeModified(cart.getId());

        CartItem item = findCartItem(cart, itemId);

        cart.getItems().remove(item);
        item.setCart(null);
        log.info("Cart item removed: cartId={}, cartItemId={}", cart.getId(), itemId);

        return mapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse clearCart(String email) {

        Cart cart = findLockedCart(email);

        ensureCartCanBeModified(cart.getId());

        int removedItemCount = cart.getItems().size();
        cart.getItems().forEach(item -> item.setCart(null));
        cart.getItems().clear();
        log.info("Cart cleared: cartId={}, removedItemCount={}", cart.getId(), removedItemCount);

        return mapper.toResponse(cart);
    }

    private void addOrIncreaseCartItem(Cart cart, Book book, int quantity) {

        cart.getItems().stream()
                .filter(item -> Objects.equals(item.getBook().getId(), book.getId()))
                .findFirst()
                .ifPresentOrElse(
                        existingItem -> existingItem.setQuantity(existingItem.getQuantity() + quantity),
                        () -> cart.getItems().add(CartItem.builder()
                                .cart(cart)
                                .book(book)
                                .quantity(quantity)
                                .build())
                );
    }

    private CartResponse createCart(String email) {

        var user = users.findLockedByEmail(email)
                .orElseThrow(() -> new AuthException(
                        BookstoreErrorConstants.INVALID_CREDENTIALS,
                        BookstoreMessageConstants.INVALID_CREDENTIALS_MESSAGE
                ));

        Cart cart = carts.findByUserEmailIgnoreCase(email)
                .orElseGet(() -> carts.save(Cart.builder().user(user).build()));

        return mapper.toResponse(cart);
    }

    private Cart findOrCreateLockedCart(String email) {

        Cart existingCart = carts.findByUserEmailIgnoreCase(email)
                .orElse(null);

        if (existingCart != null) {
            return carts.findLockedByUserEmail(email)
                    .orElseThrow(() -> new CartException(
                            BookstoreErrorConstants.CART_NOT_FOUND,
                            BookstoreMessageConstants.CART_NOT_FOUND_MESSAGE
                    ));
        }

        var user = users.findLockedByEmail(email)
                .orElseThrow(() -> new AuthException(
                        BookstoreErrorConstants.INVALID_CREDENTIALS,
                        BookstoreMessageConstants.INVALID_CREDENTIALS_MESSAGE
                ));

        Cart cart = carts.findByUserEmailIgnoreCase(email)
                .orElseGet(() -> carts.saveAndFlush(Cart.builder().user(user).build()));

        return carts.findLockedByUserEmail(email)
                .orElseThrow(() -> new CartException(
                        BookstoreErrorConstants.CART_NOT_FOUND,
                        BookstoreMessageConstants.CART_NOT_FOUND_MESSAGE
                ));
    }

    private Cart findLockedCart(String email) {

        return carts.findLockedByUserEmail(email)
                .orElseThrow(() -> new CartException(
                        BookstoreErrorConstants.CART_NOT_FOUND,
                        BookstoreMessageConstants.CART_NOT_FOUND_MESSAGE
                ));
    }

    private Book findBook(Long bookId) {

        return books.findById(bookId)
                .orElseThrow(() -> new BookException(
                        BookstoreErrorConstants.BOOK_NOT_FOUND,
                        BookstoreMessageConstants.BOOK_NOT_FOUND_MESSAGE
                ));
    }

    private int getCartQuantity(Cart cart, Long bookId) {

        return cart.getItems().stream()
                .filter(item -> Objects.equals(
                        item.getBook().getId(), bookId))
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    private void validateAvailableStock(
            Cart cart,
            Book book,
            int requestedQuantity) {

        int cartQuantity = getCartQuantity(cart, book.getId());
        int availableStock = book.getStockQuantity();

        int maxAdditionalQuantity = Math.max(
                0,
                availableStock - cartQuantity
        );

        if (requestedQuantity > maxAdditionalQuantity) {
            throw new CartException(
                    BookstoreErrorConstants.INSUFFICIENT_STOCK,
                    BookstoreMessageConstants.YOU_CAN_ADD_ONLY_MESSAGE
                            + maxAdditionalQuantity
                            + BookstoreMessageConstants.ADDITIONAL_COPIES_MESSAGE
                            + cartQuantity
                            + BookstoreMessageConstants.COPIES_ALREADY_IN_CART_MESSAGE
                            + BookstoreMessageConstants.AVAILABLE_STOCK_MESSAGE
                            + availableStock
            );
        }
    }

    private void validateUpdatedQuantity(
            CartItem item,
            int requestedQuantity) {

        int availableStock = item.getBook().getStockQuantity();

        if (requestedQuantity > availableStock) {
            throw new CartException(
                    BookstoreErrorConstants.INSUFFICIENT_STOCK,
                    BookstoreMessageConstants.MAXIMUM_COPIES_MESSAGE
                            + availableStock
                            + BookstoreMessageConstants.COPIES_OF_BOOK_MESSAGE
            );
        }
    }

    private void ensureCartCanBeModified(Long cartId) {

        if (orders.existsByCartIdAndStatus(cartId, OrderStatus.PENDING)) {
            throw new CartException(
                    BookstoreErrorConstants.CART_PENDING_CHECKOUT,
                    BookstoreMessageConstants.CART_PENDING_CHECKOUT_MESSAGE
            );
        }
    }

    private CartItem findCartItem(Cart cart, Long itemId) {

        return cart.getItems().stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new CartException(
                        BookstoreErrorConstants.CART_ITEM_NOT_FOUND,
                        BookstoreMessageConstants.CART_ITEM_NOT_FOUND_MESSAGE
                ));
    }
}
