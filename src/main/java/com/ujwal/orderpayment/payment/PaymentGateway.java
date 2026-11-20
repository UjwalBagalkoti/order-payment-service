package com.ujwal.orderpayment.payment;

import java.math.BigDecimal;

/** Boundary to the external payment provider. Real providers honour the idempotency key too. */
public interface PaymentGateway {

    GatewayResult charge(Long orderId, BigDecimal amount, String idempotencyKey);

    record GatewayResult(boolean success, String reference) {
    }
}
