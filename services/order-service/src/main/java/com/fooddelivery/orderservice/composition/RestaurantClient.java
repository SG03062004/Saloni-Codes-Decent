package com.fooddelivery.orderservice.composition;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class RestaurantClient {

  private final WebClient client;

  public RestaurantClient(@Qualifier("restaurantWebClient") WebClient client) {
    this.client = client;
  }

  @CircuitBreaker(name = "restaurant", fallbackMethod = "fallback")
  public Map<String, Object> getRestaurant(String restaurantId) {
    return client
        .get()
        .uri("/restaurants/{id}", restaurantId)
        .retrieve()
        .bodyToMono(Map.class)
        .cast(Map.class)
        .map(m -> (Map<String, Object>) m)
        .block();
  }

  @SuppressWarnings("unused")
  Map<String, Object> fallback(String restaurantId, Throwable t) {
    return Map.of("id", restaurantId, "unavailable", true);
  }
}
