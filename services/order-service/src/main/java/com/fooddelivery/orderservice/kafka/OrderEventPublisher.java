package com.fooddelivery.orderservice.kafka;

import com.fooddelivery.orderservice.kafka.dto.EventEnvelope;
import com.fooddelivery.orderservice.kafka.dto.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String orderCreatedTopic;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${kafka.topics.order-created}") String orderCreatedTopic) {
        this.kafkaTemplate     = kafkaTemplate;
        this.orderCreatedTopic = orderCreatedTopic;
    }

    public void publishOrderCreated(OrderCreatedEvent payload) {
        var envelope = EventEnvelope.of(UUID.randomUUID().toString(), "order-created", payload);
        kafkaTemplate.send(orderCreatedTopic, payload.orderId(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order-created orderId={}", payload.orderId(), ex);
                    } else {
                        log.info("Published order-created orderId={} partition={} offset={}",
                                payload.orderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
