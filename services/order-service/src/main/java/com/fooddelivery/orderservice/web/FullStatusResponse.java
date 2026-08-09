package com.fooddelivery.orderservice.web;

import com.fooddelivery.orderservice.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FullStatusResponse(
    String orderId,
    String customerId,
    OrderStatus orderStatus,
    int totalCents,
    Instant createdAt,
    List<OrderResponse.ItemDto> items,
    Map<String, Object> restaurant,
    Map<String, Object> delivery) {}
