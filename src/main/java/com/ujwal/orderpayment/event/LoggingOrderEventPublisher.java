package com.ujwal.orderpayment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default publisher (app.events.type=log): just logs. Used in tests and on hosts without Kafka. */
@Component
@ConditionalOnProperty(name = "app.events.type", havingValue = "log", matchIfMissing = true)
public class LoggingOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOrderEventPublisher.class);

    @Override
    public void publish(OrderPaidEvent event) {
        log.info("OrderPaid event: {}", event);
    }
}
