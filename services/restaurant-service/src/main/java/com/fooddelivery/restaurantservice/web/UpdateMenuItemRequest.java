package com.fooddelivery.restaurantservice.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateMenuItemRequest(
    @NotBlank String name, @Min(0) int priceCents, boolean available) {}
