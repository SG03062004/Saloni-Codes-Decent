package com.fooddelivery.restaurantservice.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "menu_items")
public class MenuItem {

  @Id private String id;

  @Column(name = "restaurant_id", nullable = false)
  private String restaurantId;

  @Column(nullable = false)
  private String name;

  @Column(name = "price_cents", nullable = false)
  private int priceCents;

  @Column(nullable = false)
  private boolean available;

  protected MenuItem() {}

  public MenuItem(String id, String restaurantId, String name, int priceCents) {
    this.id = id;
    this.restaurantId = restaurantId;
    this.name = name;
    this.priceCents = priceCents;
    this.available = true;
  }

  public void update(String name, int priceCents, boolean available) {
    this.name = name;
    this.priceCents = priceCents;
    this.available = available;
  }

  public String getId() {
    return id;
  }

  public String getRestaurantId() {
    return restaurantId;
  }

  public String getName() {
    return name;
  }

  public int getPriceCents() {
    return priceCents;
  }

  public boolean isAvailable() {
    return available;
  }
}
