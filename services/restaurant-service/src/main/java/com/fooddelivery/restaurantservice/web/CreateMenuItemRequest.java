package com.fooddelivery.restaurantservice.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateMenuItemRequest(@NotBlank String name, @Min(0) int priceCents) {}
