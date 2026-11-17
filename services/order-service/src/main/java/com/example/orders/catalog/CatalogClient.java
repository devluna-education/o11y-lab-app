package com.example.orders.catalog;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for catalog-service. Uses the JDK HttpURLConnection under the hood so that
 * any agent-based instrumentation can pick up the outbound calls without extra libraries.
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder, CatalogProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Product getProduct(long productId) {
        log.debug("Fetching product {} from catalog", productId);
        return restClient.get()
                .uri("/products/{id}", productId)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (request, response) -> {
                    throw new ProductNotFoundException(productId);
                })
                .body(Product.class);
    }

    public void reserveStock(long productId, int quantity) {
        log.debug("Reserving {} units of product {}", quantity, productId);
        restClient.post()
                .uri("/products/{id}/reserve", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity))
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (request, response) -> {
                    throw new ProductNotFoundException(productId);
                })
                .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(), (request, response) -> {
                    throw new OutOfStockException(productId, quantity);
                })
                .toBodilessEntity();
    }
}
