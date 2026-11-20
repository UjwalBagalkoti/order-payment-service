package com.ujwal.orderpayment.order;

import com.ujwal.orderpayment.common.ConflictException;
import com.ujwal.orderpayment.common.NotFoundException;
import com.ujwal.orderpayment.order.OrderDtos.OrderResponse;
import com.ujwal.orderpayment.order.OrderDtos.PlaceOrderRequest;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_ATTEMPTS = 8;

    private final OrderTx tx;
    private final OrderRepository orders;

    public OrderService(OrderTx tx, OrderRepository orders) {
        this.tx = tx;
        this.orders = orders;
    }

    /** Not @Transactional on purpose: each retry must get a fresh transaction and fresh data. */
    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.place(userId, request);
            } catch (OptimisticLockingFailureException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ConflictException("Too many concurrent updates, please retry");
                }
                log.debug("Optimistic lock conflict placing order (attempt {}), retrying", attempt);
                backoff(attempt);
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> mine(Long userId, Pageable pageable) {
        return orders.findByUserId(userId, pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, Long orderId) {
        Order order = orders.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        return OrderResponse.from(order);
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 20L * attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException("Interrupted while retrying");
        }
    }
}
