package com.fooddelivery.orderservice.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateOrderRequest(
    @NotBlank String customerId,
    @NotBlank String restaurantId,
    @NotEmpty @Valid List<OrderItemRequest> items,
    @NotNull @Valid DeliveryAddressRequest deliveryAddress) {}
