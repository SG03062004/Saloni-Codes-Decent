package com.fooddelivery.deliveryservice;

import java.time.Instant;
import java.util.UUID;

public class OrderDeliveredEvent {
  public String eventId = UUID.randomUUID().toString();
  public String eventType = "order-delivered";
  public String timestamp = Instant.now().toString();
  public String version = "1";
  public Payload payload;

  public static class Payload {
    public String orderId;
    public String driverId;
    public String deliveredAt;
  }

  public static OrderDeliveredEvent of(String orderId, String driverId, Instant deliveredAt) {
    var e = new OrderDeliveredEvent();
    e.payload = new Payload();
    e.payload.orderId = orderId;
    e.payload.driverId = driverId;
    e.payload.deliveredAt = deliveredAt.toString();
    return e;
  }
}
