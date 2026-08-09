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
            d ->
                ResponseEntity.ok(
                    Map.of(
                        "orderId", d.getOrderId(),
                        "driverId", d.getDriverId(),
                        "status", d.getStatus().name(),
                        "etaMinutes", d.getEtaMinutes() != null ? d.getEtaMinutes() : 0,
                        "deliveredAt",
                            d.getDeliveredAt() != null ? d.getDeliveredAt().toString() : null)))
        .orElse(
            ResponseEntity.status(404)
                .body(Map.of("error", "Delivery not found for orderId=" + orderId)));
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

    var delivery = deliveryService.advanceStatus(orderId, next);
    return ResponseEntity.ok(
        Map.of(
            "orderId", delivery.getOrderId(),
            "status", delivery.getStatus().name()));
  }
}
