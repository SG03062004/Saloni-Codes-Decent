package com.fooddelivery.restaurantservice.web;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RestaurantControllerIntegrationTest {

  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("restaurant_db")
          .withUsername("root")
          .withPassword("root");

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    assumeTrue(isDockerAvailable(), "Docker not available — skipping integration tests");
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void createRestaurant_returns201() throws Exception {
    mockMvc
        .perform(
            post("/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Test Bistro", "avgPrepTimeMinutes", 20))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Test Bistro"))
        .andExpect(jsonPath("$.avgPrepTimeMinutes").value(20))
        .andExpect(jsonPath("$.isOpen").value(true));
  }

  @Test
  void createRestaurant_returns400ForMissingName() throws Exception {
    mockMvc
        .perform(
            post("/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("avgPrepTimeMinutes", 20))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getRestaurant_returns404ForUnknownId() throws Exception {
    mockMvc.perform(get("/restaurants/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void menuCrud_fullLifecycle() throws Exception {
    // Create restaurant
    var createResult =
        mockMvc
            .perform(
                post("/restaurants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("name", "Menu Test Restaurant", "avgPrepTimeMinutes", 15))))
            .andExpect(status().isCreated())
            .andReturn();

    String restaurantId =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    // Add menu item
    var itemResult =
        mockMvc
            .perform(
                post("/restaurants/" + restaurantId + "/menu")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("name", "Margherita", "priceCents", 1200))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Margherita"))
            .andExpect(jsonPath("$.available").value(true))
            .andReturn();

    String itemId =
        objectMapper.readTree(itemResult.getResponse().getContentAsString()).get("id").asText();

    // List menu
    mockMvc
        .perform(get("/restaurants/" + restaurantId + "/menu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Margherita"));

    // Update item
    mockMvc
        .perform(
            put("/restaurants/" + restaurantId + "/menu/" + itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Margherita XL", "priceCents", 1500, "available", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priceCents").value(1500));

    // Delete item
    mockMvc
        .perform(delete("/restaurants/" + restaurantId + "/menu/" + itemId))
        .andExpect(status().isNoContent());
  }

  private static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Throwable t) {
      return false;
    }
  }
}
