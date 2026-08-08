package com.fooddelivery.restaurantservice.kafka.dto;

import java.time.Instant;
import java.util.List;

public record OrderCreatedEvent(
        String        orderId,
        String        customerId,
        String        restaurantId,
        List<ItemDto> items,
        AddressDto    deliveryAddress,
        int           totalCents,
        Instant       timestamp
) {
    public record ItemDto(String menuItemId, int quantity, int unitPriceCents) {}
    public record AddressDto(double lat, double lng, String street, String city) {}
}
