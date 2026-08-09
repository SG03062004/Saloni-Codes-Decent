package com.fooddelivery.orderservice.composition;

import com.fooddelivery.orderservice.domain.OrderItem;
import com.fooddelivery.orderservice.repository.OrderItemRepository;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.web.FullStatusResponse;
import com.fooddelivery.orderservice.web.OrderResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FullStatusService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final RestaurantClient restaurantClient;
  private final DeliveryClient deliveryClient;

  public FullStatusService(
      OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      RestaurantClient restaurantClient,
      DeliveryClient deliveryClient) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.restaurantClient = restaurantClient;
    this.deliveryClient = deliveryClient;
  }

  public FullStatusResponse getFullStatus(String orderId) {
    var order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

    List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
    var itemDtos =
        items.stream()
            .map(
                i ->
                    new OrderResponse.ItemDto(
                        i.getMenuItemId(), i.getQuantity(), i.getUnitPriceCents()))
            .toList();

    var restaurant = restaurantClient.getRestaurant(order.getRestaurantId());
    var delivery = deliveryClient.getDelivery(orderId);

    return new FullStatusResponse(
        order.getId(),
        order.getCustomerId(),
        order.getStatus(),
        order.getTotalCents(),
        order.getCreatedAt(),
        itemDtos,
        restaurant,
        delivery);
  }
}
