package com.ujwal.orderpayment.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.events.type", havingValue = "kafka")
public class KafkaTopicConfig {

    public static final String ORDERS_PAID_TOPIC = "orders.paid";

    @Bean
    NewTopic ordersPaidTopic() {
        // replicas(1): single-broker setups (docker-compose) cannot create a topic with more.
        return TopicBuilder.name(ORDERS_PAID_TOPIC).partitions(1).replicas(1).build();
    }
}
