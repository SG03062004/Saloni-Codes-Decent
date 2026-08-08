package com.fooddelivery.orderservice.kafka.dto;

public record OrderAcceptedEvent(
        String orderId,
        String restaurantId,
        int    prepTimeMinutes
) {}
