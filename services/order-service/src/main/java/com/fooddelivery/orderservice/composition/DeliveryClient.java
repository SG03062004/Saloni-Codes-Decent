package com.fooddelivery.orderservice.composition;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DeliveryClient {

  private final WebClient client;

  public DeliveryClient(@Qualifier("deliveryWebClient") WebClient client) {
    this.client = client;
  }

  @CircuitBreaker(name = "delivery", fallbackMethod = "fallback")
  public Map<String, Object> getDelivery(String orderId) {
    return client
        .get()
        .uri("/deliveries/by-order/{orderId}", orderId)
        .retrieve()
        .bodyToMono(Map.class)
        .cast(Map.class)
        .map(m -> (Map<String, Object>) m)
        .block();
  }

  @SuppressWarnings("unused")
  Map<String, Object> fallback(String orderId, Throwable t) {
    return Map.of("orderId", orderId, "unavailable", true);
  }
}
