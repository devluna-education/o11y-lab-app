package com.example.orders.order;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background job that moves orders through CREATED -> PAID -> SHIPPED.
 * Produces non-HTTP (scheduled) transactions with database work.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "fulfillment", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderFulfillmentJob {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentJob.class);
    private static final int BATCH_SIZE = 50;

    private final OrderRepository repository;
    private final Duration stepDelay;

    public OrderFulfillmentJob(OrderRepository repository,
                               @Value("${fulfillment.step-delay:15s}") Duration stepDelay) {
        this.repository = repository;
        this.stepDelay = stepDelay;
    }

    @Scheduled(fixedDelayString = "${fulfillment.interval-ms:10000}")
    @Transactional
    public void advanceOrders() {
        OffsetDateTime threshold = OffsetDateTime.now().minus(stepDelay);
        int advanced = 0;
        for (OrderStatus status : List.of(OrderStatus.PAID, OrderStatus.CREATED)) {
            List<Order> batch = repository.findByStatusAndUpdatedAtBefore(status, threshold, PageRequest.of(0, BATCH_SIZE));
            batch.forEach(Order::advanceStatus);
            advanced += batch.size();
        }
        if (advanced > 0) {
            log.info("Fulfillment job advanced {} orders", advanced);
        }
    }
}
