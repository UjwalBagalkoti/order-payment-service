package com.ujwal.orderpayment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Asynchronous side effect of a payment. A real system would send an email/SMS here. */
@Component
@ConditionalOnProperty(name = "app.events.type", havingValue = "kafka")
public class OrderNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationConsumer.class);

    @KafkaListener(topics = KafkaTopicConfig.ORDERS_PAID_TOPIC, groupId = "notifications")
    public void onOrderPaid(String payload) {
        log.info("Notification: sending order-paid confirmation for {}", payload);
    }
}
