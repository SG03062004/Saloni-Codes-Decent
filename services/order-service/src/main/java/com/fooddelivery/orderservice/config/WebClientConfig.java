package com.fooddelivery.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean("restaurantWebClient")
  public WebClient restaurantWebClient(@Value("${clients.restaurant-service}") String baseUrl) {
    return WebClient.builder().baseUrl(baseUrl).build();
  }

  @Bean("deliveryWebClient")
  public WebClient deliveryWebClient(@Value("${clients.delivery-service}") String baseUrl) {
    return WebClient.builder().baseUrl(baseUrl).build();
  }
}
