package com.fooddelivery.orderservice.kafka.dto;

public record DriverAssignedEvent(
        String   orderId,
        String   driverId,
        int      etaMinutes,
        GeoPoint driverLocation,
        GeoPoint restaurantLocation,
        GeoPoint deliveryLocation
) {}
