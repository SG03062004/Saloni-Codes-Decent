# Kafka Event Contracts — Phase 1

All events use JSON. Every envelope includes `eventId`, `eventType`, `timestamp`, and `version`.

---

## order.created

**Producer:** order-service  
**Consumers:** restaurant-service, assignment-service

```json
{
  "eventId": "uuid-v4",
  "eventType": "order.created",
  "timestamp": "2025-07-01T10:00:00Z",
  "version": "1",
  "payload": {
    "orderId": "uuid-v4",
    "customerId": "uuid-v4",
    "restaurantId": "uuid-v4",
    "items": [
      { "menuItemId": "uuid-v4", "quantity": 2, "unitPriceCents": 1200 }
    ],
    "deliveryAddress": {
      "lat": 37.7749,
      "lng": -122.4194,
      "street": "<street>",
      "city": "<city>"
    },
    "totalCents": 2400
  }
}
```

---

## order.accepted

**Producer:** restaurant-service  
**Consumers:** assignment-service

```json
{
  "eventId": "uuid-v4",
  "eventType": "order.accepted",
  "timestamp": "2025-07-01T10:01:00Z",
  "version": "1",
  "payload": {
    "orderId": "uuid-v4",
    "restaurantId": "uuid-v4",
    "estimatedPrepMinutes": 15
  }
}
```

---

## order.rejected

**Producer:** restaurant-service  
**Consumers:** order-service

```json
{
  "eventId": "uuid-v4",
  "eventType": "order.rejected",
  "timestamp": "2025-07-01T10:01:00Z",
  "version": "1",
  "payload": {
    "orderId": "uuid-v4",
    "reason": "RESTAURANT_CLOSED | ITEM_UNAVAILABLE | CAPACITY_EXCEEDED"
  }
}
```

---

## driver.assigned

**Producer:** assignment-service  
**Consumers:** delivery-service, eta-service

```json
{
  "eventId": "uuid-v4",
  "eventType": "driver.assigned",
  "timestamp": "2025-07-01T10:02:00Z",
  "version": "1",
  "payload": {
    "orderId": "uuid-v4",
    "driverId": "uuid-v4",
    "driverLocation": { "lat": 37.7800, "lng": -122.4100 },
    "restaurantLocation": { "lat": 37.7750, "lng": -122.4180 },
    "deliveryLocation": { "lat": 37.7749, "lng": -122.4194 }
  }
}
```

---

## eta.updated

**Producer:** eta-service  
**Consumers:** delivery-service, ops-dashboard

```json
{
  "eventId": "uuid-v4",
  "eventType": "eta.updated",
  "timestamp": "2025-07-01T10:03:00Z",
  "version": "1",
  "payload": {
    "orderId": "uuid-v4",
    "driverId": "uuid-v4",
    "etaMinutes": 22,
    "etaTimestamp": "2025-07-01T10:25:00Z"
  }
}
```

---

## delivery.completed

**Producer:** delivery-service  
**Consumers:** order-service, ops-dashboard

```json
{
  "eventId": "uuid-v4",
  "eventType": "delivery.completed",
  "timestamp": "2025-07-01T10:28:00Z",
  "version": "1",
  "payload": {
    "orderId": "uuid-v4",
    "driverId": "uuid-v4",
    "deliveredAt": "2025-07-01T10:28:00Z"
  }
}
```
