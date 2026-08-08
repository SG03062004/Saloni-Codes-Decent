package com.fooddelivery.deliveryservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryKafkaConsumer.class);

    private final DeliveryService deliveryService;
    private final ObjectMapper mapper;

    public DeliveryKafkaConsumer(DeliveryService deliveryService, ObjectMapper mapper) {
        this.deliveryService = deliveryService;
        this.mapper          = mapper;
    }

    @KafkaListener(topics = "driver-assigned", groupId = "delivery-service-group")
    public void onDriverAssigned(String message) {
        try {
            var event = mapper.readValue(message, DriverAssignedEvent.class);
            deliveryService.createFromEvent(event);
        } catch (Exception e) {
            log.error("failed to process driver-assigned message", e);
            // Let Spring Kafka's default error handler manage retries/DLQ
        }
    }
}
