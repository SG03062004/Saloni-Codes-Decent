package com.fooddelivery.deliveryservice;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deliveries")
public class Delivery {

  @Id private String id;

  @Column(name = "order_id", nullable = false, unique = true)
  private String orderId;

  @Column(name = "driver_id", nullable = false)
  private String driverId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeliveryStatus status;

  @Column(name = "eta_minutes")
  private Integer etaMinutes;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  // Getters / setters

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getDriverId() {
    return driverId;
  }

  public void setDriverId(String driverId) {
    this.driverId = driverId;
  }

  public DeliveryStatus getStatus() {
    return status;
  }

  public void setStatus(DeliveryStatus status) {
    this.status = status;
  }

  public Integer getEtaMinutes() {
    return etaMinutes;
  }

  public void setEtaMinutes(Integer etaMinutes) {
    this.etaMinutes = etaMinutes;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Instant deliveredAt) {
    this.deliveredAt = deliveredAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
