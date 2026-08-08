package com.fooddelivery.orderservice.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderItemRequest(
        @NotBlank String menuItemId,
        @Min(1)   int    quantity,
        @Min(0)   int    unitPriceCents
) {}
