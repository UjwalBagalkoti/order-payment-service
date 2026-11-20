package com.ujwal.orderpayment.payment;

import com.ujwal.orderpayment.common.BadRequestException;
import com.ujwal.orderpayment.common.ConflictException;
import com.ujwal.orderpayment.common.NotFoundException;
import com.ujwal.orderpayment.event.OrderEventPublisher;
import com.ujwal.orderpayment.event.OrderPaidEvent;
import com.ujwal.orderpayment.order.Order;
import com.ujwal.orderpayment.order.OrderRepository;
import com.ujwal.orderpayment.order.OrderStatus;
import com.ujwal.orderpayment.payment.PaymentGateway.GatewayResult;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Idempotent payment flow. The same Idempotency-Key always yields the same payment, so a client
 * that times out and retries can never be charged twice.
 *
 * Deliberately not @Transactional: the gateway call is slow and must not hold a DB connection.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final PaymentTx tx;
    private final PaymentGateway gateway;
    private final OrderEventPublisher events;

    public PaymentService(PaymentRepository payments, OrderRepository orders, PaymentTx tx,
                          PaymentGateway gateway, OrderEventPublisher events) {
        this.payments = payments;
        this.orders = orders;
        this.tx = tx;
        this.gateway = gateway;
        this.events = events;
    }

    public PaymentResponse pay(Long userId, Long orderId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new BadRequestException("Idempotency-Key header is required (1-64 characters)");
        }

        // 1. Replay: this key was already used.
        Optional<Payment> existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), userId, orderId);
        }

        // 2. The order must be ours and still payable.
        Order order = orders.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            // A concurrent retry with the same key may have just finished: replay instead of failing.
            Optional<Payment> late = payments.findByIdempotencyKey(idempotencyKey);
            if (late.isPresent()) {
                return replay(late.get(), userId, orderId);
            }
            throw new ConflictException("Order is not payable (status " + order.getStatus() + ")");
        }

        // 3. Claim the payment. Unique constraints make concurrent duplicates fail here.
        Payment payment;
        try {
            payment = tx.createInitiated(orderId, order.getTotalAmount(), idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            Optional<Payment> raced = payments.findByIdempotencyKey(idempotencyKey);
            if (raced.isPresent()) {
                return replay(raced.get(), userId, orderId);
            }
            throw new ConflictException("A payment for this order is already in progress or completed");
        }

        // 4. Call the provider outside any DB transaction.
        GatewayResult result;
        try {
            result = gateway.charge(orderId, order.getTotalAmount(), idempotencyKey);
        } catch (RuntimeException e) {
            log.warn("Payment gateway error for order {}", orderId, e);
            result = new GatewayResult(false, null);
        }

        // 5. Record the outcome, then announce it.
        Payment done = tx.complete(payment.getId(), result.success());
        if (result.success()) {
            events.publish(new OrderPaidEvent(orderId, userId, done.getAmount(), Instant.now()));
        }
        return PaymentResponse.from(done);
    }

    /** A key is bound to one user and one order; reusing it elsewhere is a client bug. */
    private PaymentResponse replay(Payment payment, Long userId, Long orderId) {
        boolean sameOrder = payment.getOrderId().equals(orderId);
        boolean sameUser = orders.findById(payment.getOrderId())
                .map(o -> o.getUserId().equals(userId))
                .orElse(false);
        if (!sameOrder || !sameUser) {
            throw new ConflictException("Idempotency-Key was already used for a different request");
        }
        return PaymentResponse.from(payment);
    }
}
