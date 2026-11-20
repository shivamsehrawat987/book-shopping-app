package com.bookstore.book_shopping_app.payment.gateway;

import java.math.BigDecimal;

public record GatewayPaymentRequest(
        BigDecimal amount,
        String currency
) {
}
