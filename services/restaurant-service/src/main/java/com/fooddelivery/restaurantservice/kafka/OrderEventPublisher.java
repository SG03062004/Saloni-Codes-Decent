package com.fooddelivery.restaurantservice.kafka;

import com.fooddelivery.restaurantservice.kafka.dto.EventEnvelope;
import com.fooddelivery.restaurantservice.kafka.dto.OrderAcceptedEvent;
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
    private final String orderAcceptedTopic;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${kafka.topics.order-accepted}") String orderAcceptedTopic) {
        this.kafkaTemplate      = kafkaTemplate;
        this.orderAcceptedTopic = orderAcceptedTopic;
    }

    public void publishOrderAccepted(OrderAcceptedEvent payload) {
        var envelope = EventEnvelope.of(UUID.randomUUID().toString(), "order-accepted", payload);
        kafkaTemplate.send(orderAcceptedTopic, payload.orderId(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order-accepted orderId={}", payload.orderId(), ex);
                    } else {
                        log.info("Published order-accepted orderId={} partition={} offset={}",
                                payload.orderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
