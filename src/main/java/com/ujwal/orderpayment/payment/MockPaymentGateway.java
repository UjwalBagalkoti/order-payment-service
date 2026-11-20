package com.ujwal.orderpayment.payment;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stand-in for a real provider. app.payment.mock-failure-rate (0.0-1.0) simulates declined payments. */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final double failureRate;

    public MockPaymentGateway(@Value("${app.payment.mock-failure-rate:0.0}") double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public GatewayResult charge(Long orderId, BigDecimal amount, String idempotencyKey) {
        boolean success = ThreadLocalRandom.current().nextDouble() >= failureRate;
        return new GatewayResult(success, "mock-" + UUID.randomUUID());
    }
}
