package com.fooddelivery.restaurantservice.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "restaurants")
public class Restaurant {

  @Id private String id;

  @Column(nullable = false)
  private String name;

  @Column(name = "is_open", nullable = false)
  private boolean isOpen;

  @Column(name = "avg_prep_time_minutes", nullable = false)
  private int avgPrepTimeMinutes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Restaurant() {}

  public Restaurant(String id, String name, int avgPrepTimeMinutes) {
    this.id = id;
    this.name = name;
    this.isOpen = true;
    this.avgPrepTimeMinutes = avgPrepTimeMinutes;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void updateDetails(String name, int avgPrepTimeMinutes) {
    this.name = name;
    this.avgPrepTimeMinutes = avgPrepTimeMinutes;
    this.updatedAt = Instant.now();
  }

  public void setOpen(boolean open) {
    this.isOpen = open;
    this.updatedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isOpen() {
    return isOpen;
  }

  public int getAvgPrepTimeMinutes() {
    return avgPrepTimeMinutes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
