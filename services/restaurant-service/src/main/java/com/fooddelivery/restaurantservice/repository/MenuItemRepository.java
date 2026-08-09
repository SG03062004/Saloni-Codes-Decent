package com.fooddelivery.restaurantservice.repository;

import com.fooddelivery.restaurantservice.domain.MenuItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, String> {
  List<MenuItem> findByRestaurantId(String restaurantId);
}
