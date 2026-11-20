package com.ujwal.orderpayment.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ujwal.orderpayment.AbstractIntegrationTest;
import com.ujwal.orderpayment.common.ConflictException;
import com.ujwal.orderpayment.order.OrderDtos.OrderItemRequest;
import com.ujwal.orderpayment.order.OrderDtos.PlaceOrderRequest;
import com.ujwal.orderpayment.product.Product;
import com.ujwal.orderpayment.product.ProductDtos.ProductRequest;
import com.ujwal.orderpayment.product.ProductDtos.ProductResponse;
import com.ujwal.orderpayment.product.ProductRepository;
import com.ujwal.orderpayment.product.ProductService;
import com.ujwal.orderpayment.user.Role;
import com.ujwal.orderpayment.user.User;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Hammers one product from many threads and checks the invariant that matters:
 * stock is never oversold and never goes negative, and every successful order is persisted.
 */
class OrderConcurrencyTest extends AbstractIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;

    @Test
    void neverOversellsUnderConcurrentOrders() throws Exception {
        final int stock = 10;
        final int threads = 100;

        User user = new User();
        user.setEmail(uniqueEmail("buyer"));
        user.setPasswordHash("not-used");
        user.setRole(Role.USER);
        final Long userId = users.save(user).getId();

        final ProductResponse product = productService.create(
                new ProductRequest("Limited-" + UUID.randomUUID(), null, new BigDecimal("99.00"), stock));
        final PlaceOrderRequest request =
                new PlaceOrderRequest(List.of(new OrderItemRequest(product.id(), 1)));

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    orderService.place(userId, request);
                    return true;
                } catch (ConflictException e) {
                    return false; // out of stock (or gave up after repeated conflicts)
                }
            }));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> f : results) {
            if (f.get(120, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        Product fresh = products.findById(product.id()).orElseThrow();
        long persistedOrders = orders.findByUserId(userId, PageRequest.of(0, 1000)).getTotalElements();

        assertTrue(succeeded > 0, "at least one order should succeed");
        assertTrue(succeeded <= stock, "oversold: " + succeeded + " orders for " + stock + " units");
        assertTrue(fresh.getStock() >= 0, "stock went negative");
        assertEquals(stock - succeeded, fresh.getStock(), "stock must equal initial minus successful orders");
        assertEquals(succeeded, persistedOrders, "every successful order must be persisted exactly once");
    }
}
