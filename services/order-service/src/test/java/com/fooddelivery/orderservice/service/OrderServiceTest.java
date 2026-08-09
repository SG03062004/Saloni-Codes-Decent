package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fooddelivery.orderservice.domain.Order;
import com.fooddelivery.orderservice.domain.OrderStatus;
import com.fooddelivery.orderservice.kafka.OrderEventPublisher;
import com.fooddelivery.orderservice.kafka.dto.OrderCreatedEvent;
import com.fooddelivery.orderservice.repository.OrderItemRepository;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.web.CreateOrderRequest;
import com.fooddelivery.orderservice.web.DeliveryAddressRequest;
import com.fooddelivery.orderservice.web.OrderItemRequest;
import com.fooddelivery.orderservice.web.OrderResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderServiceTest {

  private OrderRepository orderRepository;
  private OrderItemRepository orderItemRepository;
  private OrderEventPublisher publisher;
  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    orderItemRepository = mock(OrderItemRepository.class);
    publisher = mock(OrderEventPublisher.class);
    orderService = new OrderService(orderRepository, orderItemRepository, publisher);

    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void createOrder_persistsAndPublishes() {
    var req =
        new CreateOrderRequest(
            "cust-1",
            "rest-1",
            List.of(new OrderItemRequest("item-1", 2, 1000)),
            new DeliveryAddressRequest(37.77, -122.41, "123 Main St", "San Francisco"));

    OrderResponse response = orderService.createOrder(req);

    assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(response.totalCents()).isEqualTo(2000);
    assertThat(response.orderId()).isNotBlank();

    verify(orderRepository).save(any(Order.class));
    ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
    verify(publisher).publishOrderCreated(captor.capture());
    assertThat(captor.getValue().deliveryAddress().lat()).isEqualTo(37.77);
  }

  @Test
  void markAccepted_transitionsStatus() {
    var order =
        new Order(
            "o1",
            "c1",
            "r1",
            1000,
            java.math.BigDecimal.valueOf(37.77),
            java.math.BigDecimal.valueOf(-122.41),
            "123 Main",
            "SF");
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

    orderService.markAccepted("o1");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    verify(orderRepository).save(order);
  }

  @Test
  void markDriverAssigned_setsEta() {
    var order =
        new Order(
            "o1",
            "c1",
            "r1",
            1000,
            java.math.BigDecimal.valueOf(37.77),
            java.math.BigDecimal.valueOf(-122.41),
            "123 Main",
            "SF");
    when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

    orderService.markDriverAssigned("o1", 25);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.DRIVER_ASSIGNED);
    assertThat(order.getEtaMinutes()).isEqualTo(25);
  }

  @Test
  void getOrder_throwsWhenNotFound() {
    when(orderRepository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> orderService.getOrder("missing"))
        .isInstanceOf(EntityNotFoundException.class);
  }
}
