package com.fooddelivery.orderservice.repository;

import com.fooddelivery.orderservice.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {}
