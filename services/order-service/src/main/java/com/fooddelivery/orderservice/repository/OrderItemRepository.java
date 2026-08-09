package com.fooddelivery.orderservice.repository;

import com.fooddelivery.orderservice.domain.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
  List<OrderItem> findByOrderId(String orderId);
}
