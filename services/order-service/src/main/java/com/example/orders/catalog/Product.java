package com.example.orders.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Product(long id, String name, String category, BigDecimal price, int stock) {
}
