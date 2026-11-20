package com.ujwal.orderpayment.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ujwal.orderpayment.AbstractIntegrationTest;
import com.ujwal.orderpayment.common.ConflictException;
import com.ujwal.orderpayment.order.OrderDtos.OrderItemRequest;
import com.ujwal.orderpayment.order.OrderDtos.PlaceOrderRequest;
import com.ujwal.orderpayment.order.OrderRepository;
import com.ujwal.orderpayment.order.OrderService;
import com.ujwal.orderpayment.order.OrderStatus;
import com.ujwal.orderpayment.product.ProductDtos.ProductRequest;
import com.ujwal.orderpayment.product.ProductService;
import com.ujwal.orderpayment.user.Role;
import com.ujwal.orderpayment.user.User;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Proves the "never charge twice" guarantee holds when requests really race. */
class PaymentConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 10;

    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository payments;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired ProductService productService;

    private Long newUserId() {
        User user = new User();
        user.setEmail(uniqueEmail("payer"));
        user.setPasswordHash("not-used");
        user.setRole(Role.USER);
        return users.save(user).getId();
    }

    private Long newOrder(Long userId) {
        long productId = productService.create(
                new ProductRequest("Race-" + UUID.randomUUID(), null, new BigDecimal("25.00"), 5)).id();
        return orderService.place(userId, new PlaceOrderRequest(List.of(new OrderItemRequest(productId, 1)))).id();
    }

    /** Runs the supplied calls all at once; a ConflictException counts as "rejected" (null). */
    private List<PaymentResponse> race(List<Supplier<PaymentResponse>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentResponse>> futures = new ArrayList<>();
        for (Supplier<PaymentResponse> call : calls) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return call.get();
                } catch (ConflictException e) {
                    return null;
                }
            }));
        }
        start.countDown();
        List<PaymentResponse> results = new ArrayList<>();
        for (Future<PaymentResponse> f : futures) {
            results.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    @Test
    void tenParallelRetriesWithTheSameKeyChargeOnce() throws Exception {
        Long userId = newUserId();
        Long orderId = newOrder(userId);
        String key = UUID.randomUUID().toString();

        List<Supplier<PaymentResponse>> calls = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            calls.add(() -> paymentService.pay(userId, orderId, key));
        }
        List<PaymentResponse> results = race(calls);

        Set<Long> distinctPaymentIds = new HashSet<>();
        for (PaymentResponse r : results) {
            if (r != null) {
                distinctPaymentIds.add(r.id());
            }
        }
        assertEquals(1, distinctPaymentIds.size(), "all callers must see the same payment");
        assertEquals(1, payments.countByOrderId(orderId), "exactly one payment row");
        assertEquals(OrderStatus.PAID, orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void tenParallelAttemptsWithDifferentKeysChargeOnce() throws Exception {
        Long userId = newUserId();
        Long orderId = newOrder(userId);

        List<Supplier<PaymentResponse>> calls = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String key = UUID.randomUUID().toString();
            calls.add(() -> paymentService.pay(userId, orderId, key));
        }
        List<PaymentResponse> results = race(calls);

        long accepted = results.stream().filter(r -> r != null).count();
        assertEquals(1, accepted, "only one of the competing payments may be accepted");
        assertEquals(1, payments.countByOrderId(orderId), "exactly one payment row");
        assertEquals(OrderStatus.PAID, orders.findById(orderId).orElseThrow().getStatus());
    }
}
