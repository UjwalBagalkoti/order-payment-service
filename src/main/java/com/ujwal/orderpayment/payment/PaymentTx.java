package com.ujwal.orderpayment.payment;

import com.ujwal.orderpayment.order.Order;
import com.ujwal.orderpayment.order.OrderRepository;
import com.ujwal.orderpayment.order.OrderStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two short database transactions around the (slow, external) gateway call.
 * Separate bean so PaymentService can catch a unique-constraint violation from
 * createInitiated() without the failed transaction poisoning its own work.
 */
@Component
public class PaymentTx {

    private final PaymentRepository payments;
    private final OrderRepository orders;

    public PaymentTx(PaymentRepository payments, OrderRepository orders) {
        this.payments = payments;
        this.orders = orders;
    }

    /** Inserts INITIATED. The unique idempotency_key and the one-active-payment-per-order index do the locking. */
    @Transactional
    public Payment createInitiated(Long orderId, BigDecimal amount, String idempotencyKey) {
        return payments.saveAndFlush(new Payment(orderId, idempotencyKey, amount, PaymentStatus.INITIATED));
    }

    @Transactional
    public Payment complete(Long paymentId, boolean success) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        payment.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        if (success) {
            Order order = orders.findById(payment.getOrderId()).orElseThrow();
            order.setStatus(OrderStatus.PAID);
        }
        return payment;
    }
}
