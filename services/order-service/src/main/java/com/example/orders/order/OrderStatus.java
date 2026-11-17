package com.example.orders.order;

public enum OrderStatus {
    CREATED,
    PAID,
    SHIPPED;

    public OrderStatus next() {
        return switch (this) {
            case CREATED -> PAID;
            case PAID, SHIPPED -> SHIPPED;
        };
    }
}
