package com.fooddelivery.restaurantservice.web;

import com.fooddelivery.restaurantservice.service.MenuItemService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/restaurants/{restaurantId}/menu")
public class MenuItemController {

  private final MenuItemService service;

  public MenuItemController(MenuItemService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<MenuItemResponse> create(
      @PathVariable String restaurantId, @Valid @RequestBody CreateMenuItemRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(restaurantId, req));
  }

  @GetMapping
  public List<MenuItemResponse> list(@PathVariable String restaurantId) {
    return service.list(restaurantId);
  }

  @PutMapping("/{itemId}")
  public MenuItemResponse update(
      @PathVariable String restaurantId,
      @PathVariable String itemId,
      @Valid @RequestBody UpdateMenuItemRequest req) {
    return service.update(restaurantId, itemId, req);
  }

  @DeleteMapping("/{itemId}")
  public ResponseEntity<Void> delete(
      @PathVariable String restaurantId, @PathVariable String itemId) {
    service.delete(restaurantId, itemId);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
  }
}
