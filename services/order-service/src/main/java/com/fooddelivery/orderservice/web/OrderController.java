package com.fooddelivery.orderservice.web;

import com.fooddelivery.orderservice.composition.FullStatusService;
import com.fooddelivery.orderservice.service.OrderService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;
  private final FullStatusService fullStatusService;

  public OrderController(OrderService orderService, FullStatusService fullStatusService) {
    this.orderService = orderService;
    this.fullStatusService = fullStatusService;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(orderService.createOrder(request));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
    return ResponseEntity.ok(orderService.getOrder(orderId));
  }

  @GetMapping("/{orderId}/full-status")
  public ResponseEntity<FullStatusResponse> getFullStatus(@PathVariable String orderId) {
    return ResponseEntity.ok(fullStatusService.getFullStatus(orderId));
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(
      org.springframework.web.bind.MethodArgumentNotValidException ex) {
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    return ResponseEntity.badRequest().body(Map.of("error", msg));
  }
}
