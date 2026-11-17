package com.example.orders.catalog;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(long productId, int quantity) {
        super("Not enough stock for product " + productId + " (requested " + quantity + ")");
    }
}
