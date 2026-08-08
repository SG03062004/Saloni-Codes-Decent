package com.fooddelivery.restaurantservice.kafka.dto;

public record OrderAcceptedEvent(
        String orderId,
        String restaurantId,
        int    prepTimeMinutes
) {}
