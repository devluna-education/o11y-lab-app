package com.example.orders.web;

import com.example.orders.catalog.OutOfStockException;
import com.example.orders.catalog.ProductNotFoundException;
import com.example.orders.order.OrderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({OrderNotFoundException.class, ProductNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        log.warn(ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(OutOfStockException.class)
    ProblemDetail handleOutOfStock(OutOfStockException ex) {
        log.warn(ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail handleCatalogError(RestClientResponseException ex) {
        log.error("catalog-service returned {}", ex.getStatusCode(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "catalog-service error: " + ex.getStatusCode());
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail handleCatalogUnavailable(ResourceAccessException ex) {
        log.error("catalog-service unreachable", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "catalog-service unavailable");
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }
}
