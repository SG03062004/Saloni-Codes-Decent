package com.fooddelivery.restaurantservice.web;

import com.fooddelivery.restaurantservice.domain.Restaurant;
import java.time.Instant;

public record RestaurantResponse(
        String  id,
        String  name,
        boolean isOpen,
        int     avgPrepTimeMinutes,
        Instant createdAt
) {
    public static RestaurantResponse from(Restaurant r) {
        return new RestaurantResponse(r.getId(), r.getName(), r.isOpen(),
                r.getAvgPrepTimeMinutes(), r.getCreatedAt());
    }
}
