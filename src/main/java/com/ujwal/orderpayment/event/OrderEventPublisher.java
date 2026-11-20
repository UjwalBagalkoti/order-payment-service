package com.ujwal.orderpayment.event;

public interface OrderEventPublisher {

    /** Must never throw: a messaging problem must not fail a payment that already succeeded. */
    void publish(OrderPaidEvent event);
}
