package com.fooddelivery.deliveryservice;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/deliveries")
public class DeliveryController {

  private final DeliveryService deliveryService;

  public DeliveryController(DeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @GetMapping("/by-order/{orderId}")
  public ResponseEntity<?> getByOrderId(@PathVariable String orderId) {
    return deliveryService
        .findByOrderId(orderId)
        .<ResponseEntity<?>>map(
            d -> {
              Map<String, Object> response = new java.util.HashMap<>();

              response.put("orderId", d.getOrderId());
              response.put("driverId", d.getDriverId());
              response.put("status", d.getStatus().name());
              response.put("etaMinutes", d.getEtaMinutes() != null ? d.getEtaMinutes() : 0);

              if (d.getDeliveredAt() != null) {
                response.put("deliveredAt", d.getDeliveredAt().toString());
              } else {
                response.put("deliveredAt", null);
              }

              return ResponseEntity.ok(response);
            })
        .orElse(
            ResponseEntity.status(404)
                .body(Map.of("error", "Delivery not found for orderId=" + orderId)));
  }

  /**
   * Test-harness only: directly seed a delivery row in ASSIGNED state, bypassing the Kafka
   * DriverAssignedEvent flow. Body: { "orderId": "...", "driverId": "..." }
   */
  @PostMapping("/seed")
  public ResponseEntity<Map<String, Object>> seed(@RequestBody Map<String, String> body) {

    String orderId = body.get("orderId");
    String driverId = body.get("driverId");

    if (orderId == null || orderId.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "orderId is required"));
    }
    if (driverId == null || driverId.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "driverId is required"));
    }

    var delivery = deliveryService.seedForTesting(orderId, driverId);
    return ResponseEntity.ok(
        Map.of(
            "orderId", delivery.getOrderId(),
            "driverId", delivery.getDriverId(),
            "status", delivery.getStatus().name()));
  }

  @PatchMapping("/{orderId}/status")
  public ResponseEntity<Map<String, Object>> updateStatus(
      @PathVariable String orderId, @RequestBody Map<String, String> body) {

    String statusStr = body.get("status");
    if (statusStr == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "status field required"));
    }

    DeliveryStatus next;
    try {
      next = DeliveryStatus.valueOf(statusStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "unknown status: " + statusStr));
    }

    try {
      var delivery = deliveryService.advanceStatus(orderId, next);
      return ResponseEntity.ok(
          Map.of(
              "orderId", delivery.getOrderId(),
              "status", delivery.getStatus().name()));
    } catch (DeliveryService.DeliveryNotFoundException ex) {
      return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    } catch (DeliveryService.InvalidStatusTransitionException ex) {
      return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
    }
  }
}
