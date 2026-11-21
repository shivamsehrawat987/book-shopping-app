
package com.bookstore.book_shopping_app.service.impl;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.entity.Cart;
import com.bookstore.book_shopping_app.entity.CartItem;
import com.bookstore.book_shopping_app.entity.Order;
import com.bookstore.book_shopping_app.exception.business.CommonException;
import com.bookstore.book_shopping_app.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CartPurchaseCleanupService {

    private final CartRepository carts;

    @Transactional(propagation = Propagation.MANDATORY)
    public void removePurchasedItems(Order order) {

        Cart cart = carts.findLockedById(order.getCartId())
                .orElseThrow(() -> new CommonException(
                        BookstoreErrorConstants.CART_NOT_FOUND,
                        BookstoreMessageConstants.CART_NOT_FOUND_MESSAGE
                ));

        order.getItems().forEach(orderItem -> {

            cart.getItems().stream()
                    .filter(cartItem -> Objects.equals(
                            cartItem.getBook().getId(),
                            orderItem.getBook().getId()
                    ))
                    .findFirst()
                    .ifPresent(cartItem -> {

                        int remainingQuantity =
                                cartItem.getQuantity() - orderItem.getQuantity();

                        if (remainingQuantity <= 0) {
                            cart.getItems().remove(cartItem);
                            cartItem.setCart(null);
                        } else {
                            cartItem.setQuantity(remainingQuantity);
                        }
                    });
        });
    }
}
