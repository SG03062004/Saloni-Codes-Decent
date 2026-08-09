package com.fooddelivery.deliveryservice;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class DeliveryIntegrationTest {

  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("delivery_db")
          .withUsername("root")
          .withPassword("root")
          .withInitScript("db/init.sql");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    // Point Kafka to a non-existent broker — consumer won't start but context loads fine
    registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
  }

  @Autowired DeliveryService deliveryService;
  @Autowired DeliveryRepository deliveryRepository;

  // Mock the producer so no real Kafka is needed for DB-focused tests
  @MockBean DeliveryKafkaProducer producer;

  @Test
  void fullLifecycle_assignedToDelivered() {
    var event = makeEvent("order-tc-1", "driver-tc-1", 30);

    // Create
    var created = deliveryService.createFromEvent(event);
    assertThat(created.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);

    // ASSIGNED → PICKED_UP
    deliveryService.advanceStatus("order-tc-1", DeliveryStatus.PICKED_UP);

    // PICKED_UP → IN_TRANSIT
    deliveryService.advanceStatus("order-tc-1", DeliveryStatus.IN_TRANSIT);

    // IN_TRANSIT → DELIVERED
    deliveryService.advanceStatus("order-tc-1", DeliveryStatus.DELIVERED);

    var final_ = deliveryRepository.findByOrderId("order-tc-1").orElseThrow();
    assertThat(final_.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    assertThat(final_.getDeliveredAt()).isNotNull();
    verify(producer).publishOrderDelivered(any());
  }

  @Test
  void idempotentCreate_doesNotDuplicate() {
    var event = makeEvent("order-tc-2", "driver-tc-2", 20);
    deliveryService.createFromEvent(event);
    deliveryService.createFromEvent(event); // second call — same orderId

    long count =
        deliveryRepository.findAll().stream()
            .filter(d -> "order-tc-2".equals(d.getOrderId()))
            .count();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void invalidTransition_isRejected() {
    var event = makeEvent("order-tc-3", "driver-tc-3", 15);
    deliveryService.createFromEvent(event);

    assertThatThrownBy(() -> deliveryService.advanceStatus("order-tc-3", DeliveryStatus.DELIVERED))
        .isInstanceOf(DeliveryService.InvalidStatusTransitionException.class);
  }

  private DriverAssignedEvent makeEvent(String orderId, String driverId, int eta) {
    var e = new DriverAssignedEvent();
    e.eventId = java.util.UUID.randomUUID().toString();
    e.eventType = "driver-assigned";
    e.timestamp = "2024-01-01T00:00:00Z";
    e.version = "1";
    e.payload = new DriverAssignedEvent.Payload();
    e.payload.orderId = orderId;
    e.payload.driverId = driverId;
    e.payload.etaMinutes = eta;
    return e;
  }
}
