package com.example.orders.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderResponse(
        Long id,
        String customerId,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal total,
        OrderStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getProductName(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotal(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
