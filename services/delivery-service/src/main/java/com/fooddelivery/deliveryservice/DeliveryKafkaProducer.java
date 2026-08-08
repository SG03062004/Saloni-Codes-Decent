package com.fooddelivery.deliveryservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryKafkaProducer.class);
    private static final String TOPIC = "order-delivered";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public DeliveryKafkaProducer(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka  = kafka;
        this.mapper = mapper;
    }

    public void publishOrderDelivered(OrderDeliveredEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            kafka.send(TOPIC, event.payload.orderId, json);
            log.info("published order-delivered orderId={}", event.payload.orderId);
        } catch (Exception e) {
            log.error("failed to publish order-delivered orderId={}", event.payload.orderId, e);
            throw new RuntimeException(e);
        }
    }
}
