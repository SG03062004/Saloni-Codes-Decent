package com.fooddelivery.orderservice.web;

import com.fooddelivery.orderservice.domain.Order;
import com.fooddelivery.orderservice.domain.OrderItem;
import com.fooddelivery.orderservice.domain.OrderStatus;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String orderId,
    OrderStatus status,
    int totalCents,
    Integer etaMinutes,
    Instant createdAt,
    List<ItemDto> items) {
  public record ItemDto(String menuItemId, int quantity, int unitPriceCents) {}

  public static OrderResponse from(Order order, List<OrderItem> items) {
    var itemDtos =
        items.stream()
            .map(i -> new ItemDto(i.getMenuItemId(), i.getQuantity(), i.getUnitPriceCents()))
            .toList();
    return new OrderResponse(
        order.getId(),
        order.getStatus(),
        order.getTotalCents(),
        order.getEtaMinutes(),
        order.getCreatedAt(),
        itemDtos);
  }
}
