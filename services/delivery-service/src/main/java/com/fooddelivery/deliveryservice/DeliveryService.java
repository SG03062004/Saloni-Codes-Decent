package com.fooddelivery.deliveryservice;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

  private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

  private final DeliveryRepository repo;
  private final DeliveryKafkaProducer producer;

  public DeliveryService(DeliveryRepository repo, DeliveryKafkaProducer producer) {
    this.repo = repo;
    this.producer = producer;
  }

  public java.util.Optional<Delivery> findByOrderId(String orderId) {
    return repo.findByOrderId(orderId);
  }

  @Transactional
  public Delivery createFromEvent(DriverAssignedEvent event) {
    var p = event.payload;
    MDC.put("orderId", p.orderId);
    MDC.put("driverId", p.driverId);
    try {
      if (repo.findByOrderId(p.orderId).isPresent()) {
        log.info("delivery already exists, skipping idempotent create");
        return repo.findByOrderId(p.orderId).get();
      }
      var delivery = new Delivery();
      delivery.setId(UUID.randomUUID().toString());
      delivery.setOrderId(p.orderId);
      delivery.setDriverId(p.driverId);
      delivery.setStatus(DeliveryStatus.ASSIGNED);
      delivery.setEtaMinutes(p.etaMinutes);
      var saved = repo.save(delivery);
      log.info("delivery created status=ASSIGNED");
      return saved;
    } finally {
      MDC.clear();
    }
  }

  /**
   * Test-harness only: seed a delivery row in ASSIGNED state directly in the DB,
   * bypassing the Kafka DriverAssignedEvent flow. Idempotent — returns existing row
   * if already present.
   */
  @Transactional
  public Delivery seedForTesting(String orderId, String driverId) {
    MDC.put("orderId", orderId);
    try {
      return repo.findByOrderId(orderId).orElseGet(() -> {
        var delivery = new Delivery();
        delivery.setId(UUID.randomUUID().toString());
        delivery.setOrderId(orderId);
        delivery.setDriverId(driverId);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        delivery.setEtaMinutes(10);
        var saved = repo.save(delivery);
        log.info("delivery seeded for testing status=ASSIGNED driverId={}", driverId);
        return saved;
      });
    } finally {
      MDC.clear();
    }
  }

  @Transactional
  public Delivery advanceStatus(String orderId, DeliveryStatus next) {
    MDC.put("orderId", orderId);
    try {
      var delivery =
          repo.findByOrderId(orderId).orElseThrow(() -> new DeliveryNotFoundException(orderId));

      if (!delivery.getStatus().canTransitionTo(next)) {
        throw new InvalidStatusTransitionException(delivery.getStatus(), next);
      }

      delivery.setStatus(next);

      if (next == DeliveryStatus.DELIVERED) {
        delivery.setDeliveredAt(Instant.now());
        var saved = repo.save(delivery);
        producer.publishOrderDelivered(
            OrderDeliveredEvent.of(orderId, delivery.getDriverId(), delivery.getDeliveredAt()));
        log.info("delivery completed status=DELIVERED");
        return saved;
      }

      log.info("delivery status advanced to {}", next);
      return repo.save(delivery);
    } finally {
      MDC.clear();
    }
  }

  // ── Domain exceptions ─────────────────────────────────────────────────────

  public static class DeliveryNotFoundException extends RuntimeException {
    public DeliveryNotFoundException(String orderId) {
      super("Delivery not found for orderId=" + orderId);
    }
  }

  public static class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(DeliveryStatus from, DeliveryStatus to) {
      super("Cannot transition from " + from + " to " + to);
    }
  }
}
