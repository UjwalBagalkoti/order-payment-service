package com.ujwal.orderpayment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes OrderPaid events to Kafka, keyed by order id so events for one order stay ordered.
 *
 * Known limitation: this is a "dual write" (DB commit, then publish). If the process dies in
 * between, the event is lost. The production fix is the transactional outbox pattern.
 */
@Component
@ConditionalOnProperty(name = "app.events.type", havingValue = "kafka")
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public KafkaOrderEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Override
    public void publish(OrderPaidEvent event) {
        try {
            String payload = mapper.writeValueAsString(event);
            kafka.send(KafkaTopicConfig.ORDERS_PAID_TOPIC, String.valueOf(event.orderId()), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish OrderPaid for order {}", event.orderId(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to publish OrderPaid for order {}", event.orderId(), e);
        }
    }
}
