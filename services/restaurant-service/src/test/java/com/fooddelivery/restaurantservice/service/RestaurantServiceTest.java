package com.fooddelivery.restaurantservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fooddelivery.restaurantservice.domain.Restaurant;
import com.fooddelivery.restaurantservice.kafka.OrderEventPublisher;
import com.fooddelivery.restaurantservice.kafka.dto.OrderAcceptedEvent;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.web.CreateRestaurantRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RestaurantServiceTest {

  private RestaurantRepository repository;
  private OrderEventPublisher publisher;
  private RestaurantService service;

  @BeforeEach
  void setUp() {
    repository = mock(RestaurantRepository.class);
    publisher = mock(OrderEventPublisher.class);
    service = new RestaurantService(repository, publisher);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void create_persistsAndReturnsResponse() {
    var response = service.create(new CreateRestaurantRequest("Burger Place", 20));
    verify(repository).save(any(Restaurant.class));
    assertThat(response.name()).isEqualTo("Burger Place");
    assertThat(response.avgPrepTimeMinutes()).isEqualTo(20);
    assertThat(response.isOpen()).isTrue();
  }

  @Test
  void acceptOrder_publishesWithCorrectPrepTime() {
    var restaurant = new Restaurant("r1", "Pizza Co", 18);
    when(repository.findById("r1")).thenReturn(Optional.of(restaurant));

    service.acceptOrder("order-1", "r1");

    ArgumentCaptor<OrderAcceptedEvent> captor = ArgumentCaptor.forClass(OrderAcceptedEvent.class);
    verify(publisher).publishOrderAccepted(captor.capture());
    assertThat(captor.getValue().orderId()).isEqualTo("order-1");
    assertThat(captor.getValue().restaurantId()).isEqualTo("r1");
    assertThat(captor.getValue().prepTimeMinutes()).isEqualTo(18);
  }

  @Test
  void acceptOrder_dropsWhenRestaurantNotFound() {
    when(repository.findById("missing")).thenReturn(Optional.empty());
    service.acceptOrder("order-1", "missing");
    verifyNoInteractions(publisher);
  }

  @Test
  void acceptOrder_dropsWhenRestaurantClosed() {
    var restaurant = new Restaurant("r1", "Closed Cafe", 15);
    restaurant.setOpen(false);
    when(repository.findById("r1")).thenReturn(Optional.of(restaurant));

    service.acceptOrder("order-1", "r1");
    verifyNoInteractions(publisher);
  }
}
