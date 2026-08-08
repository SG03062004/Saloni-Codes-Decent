package com.fooddelivery.restaurantservice.repository;

import com.fooddelivery.restaurantservice.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, String> {}
