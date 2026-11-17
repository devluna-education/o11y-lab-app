package com.example.orders.order;

import com.example.orders.catalog.CatalogClient;
import com.example.orders.catalog.Product;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final CatalogClient catalogClient;

    public OrderService(OrderRepository repository, CatalogClient catalogClient) {
        this.repository = repository;
        this.catalogClient = catalogClient;
    }

    /**
     * Places an order: looks the product up in the catalog, reserves stock and persists the order.
     * Each step is a distinct downstream call (HTTP, HTTP, JDBC), which makes for a useful trace.
     */
    public Order placeOrder(CreateOrderRequest request) {
        Product product = catalogClient.getProduct(request.productId());
        catalogClient.reserveStock(product.id(), request.quantity());

        Order order = repository.save(new Order(
                request.customerId(), product.id(), product.name(), request.quantity(), product.price()));

        log.info("Order {} placed: customer={} product={} quantity={} total={}",
                order.getId(), order.getCustomerId(), order.getProductId(), order.getQuantity(), order.getTotal());
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(long id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> listRecent(int limit) {
        return repository.findByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public Map<OrderStatus, Long> statusSummary() {
        Map<OrderStatus, Long> summary = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            summary.put(status, 0L);
        }
        repository.countByStatus().forEach(row -> summary.put(row.getStatus(), row.getCount()));
        return summary;
    }
}
