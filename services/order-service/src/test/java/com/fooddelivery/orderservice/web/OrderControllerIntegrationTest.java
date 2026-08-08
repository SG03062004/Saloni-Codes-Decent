package com.fooddelivery.orderservice.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.orderservice.repository.OrderRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderControllerIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("order_db")
            .withUsername("root")
            .withPassword("root");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // assumeTrue skips all tests in this class if Docker is unavailable,
        // preventing the context from trying to connect to non-existent containers.
        assumeTrue(isDockerAvailable(), "Docker not available — skipping integration tests");
        registry.add("spring.datasource.url",           mysql::getJdbcUrl);
        registry.add("spring.datasource.username",      mysql::getUsername);
        registry.add("spring.datasource.password",      mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers",  kafka::getBootstrapServers);
    }

    @Autowired MockMvc         mockMvc;
    @Autowired ObjectMapper    objectMapper;
    @Autowired OrderRepository orderRepository;

    @Test
    void postOrder_persistsRowAndPublishesToKafka() throws Exception {
        var body = Map.of(
                "customerId",   "cust-abc",
                "restaurantId", "rest-xyz",
                "items", List.of(Map.of(
                        "menuItemId", "item-1", "quantity", 2, "unitPriceCents", 1500)),
                "deliveryAddress", Map.of(
                        "lat", 37.77, "lng", -122.41,
                        "street", "1 Market St", "city", "San Francisco"));

        var result = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalCents").value(3000))
                .andReturn();

        String orderId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("orderId").asText();

        assertThat(orderRepository.findById(orderId)).isPresent();

        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,        kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,                 "test-verifier",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,        "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("order-created"));
            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.isEmpty()).isFalse();
            assertThat(records.iterator().next().key()).isEqualTo(orderId);
        }
    }

    @Test
    void getOrder_returns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/orders/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postOrder_returns400ForMissingFields() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
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
