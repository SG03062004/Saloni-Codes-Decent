package com.fooddelivery.restaurantservice.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateRestaurantRequest(
    @NotBlank String name, @Min(1) @Max(120) int avgPrepTimeMinutes) {}
