package com.example.orders.order;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByOrderByCreatedAtDesc(Pageable pageable);

    List<Order> findByStatusAndUpdatedAtBefore(OrderStatus status, OffsetDateTime before, Pageable pageable);

    @Query("select o.status as status, count(o) as count from Order o group by o.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        OrderStatus getStatus();

        long getCount();
    }
}
