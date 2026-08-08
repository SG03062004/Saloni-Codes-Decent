package com.fooddelivery.restaurantservice.kafka;

import com.fooddelivery.restaurantservice.kafka.dto.EventEnvelope;
import com.fooddelivery.restaurantservice.kafka.dto.OrderCreatedEvent;
import com.fooddelivery.restaurantservice.service.RestaurantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final RestaurantService restaurantService;

    public OrderEventConsumer(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @KafkaListener(topics = "${kafka.topics.order-created}", groupId = "restaurant-service-group")
    public void onOrderCreated(@Payload EventEnvelope<OrderCreatedEvent> envelope) {
        OrderCreatedEvent event = envelope.payload();
        MDC.put("orderId",      event.orderId());
        MDC.put("restaurantId", event.restaurantId());
        try {
            log.info("Received order-created totalCents={}", event.totalCents());
            restaurantService.acceptOrder(event.orderId(), event.restaurantId());
        } finally {
            MDC.remove("orderId");
            MDC.remove("restaurantId");
        }
    }
}
