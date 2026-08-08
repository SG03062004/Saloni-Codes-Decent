package com.fooddelivery.restaurantservice.web;

import com.fooddelivery.restaurantservice.domain.MenuItem;

public record MenuItemResponse(
        String  id,
        String  restaurantId,
        String  name,
        int     priceCents,
        boolean available
) {
    public static MenuItemResponse from(MenuItem m) {
        return new MenuItemResponse(m.getId(), m.getRestaurantId(),
                m.getName(), m.getPriceCents(), m.isAvailable());
    }
}
