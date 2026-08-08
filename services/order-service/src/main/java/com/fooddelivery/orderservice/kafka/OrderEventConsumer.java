package com.fooddelivery.orderservice.kafka;

import com.fooddelivery.orderservice.kafka.dto.DriverAssignedEvent;
import com.fooddelivery.orderservice.kafka.dto.EventEnvelope;
import com.fooddelivery.orderservice.kafka.dto.OrderAcceptedEvent;
import com.fooddelivery.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderService orderService;

    public OrderEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "${kafka.topics.order-accepted}", groupId = "order-service-group")
    public void onOrderAccepted(@Payload EventEnvelope<OrderAcceptedEvent> envelope) {
        OrderAcceptedEvent event = envelope.payload();
        MDC.put("orderId", event.orderId());
        try {
            log.info("Received order-accepted orderId={}", event.orderId());
            orderService.markAccepted(event.orderId());
        } finally {
            MDC.remove("orderId");
        }
    }

    @KafkaListener(topics = "${kafka.topics.driver-assigned}", groupId = "order-service-group")
    public void onDriverAssigned(@Payload EventEnvelope<DriverAssignedEvent> envelope) {
        DriverAssignedEvent event = envelope.payload();
        MDC.put("orderId", event.orderId());
        try {
            log.info("Received driver-assigned orderId={} etaMinutes={}", event.orderId(), event.etaMinutes());
            orderService.markDriverAssigned(event.orderId(), event.etaMinutes());
        } finally {
            MDC.remove("orderId");
        }
    }
}
