package com.fooddelivery.deliveryservice;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, String> {
  Optional<Delivery> findByOrderId(String orderId);
}
