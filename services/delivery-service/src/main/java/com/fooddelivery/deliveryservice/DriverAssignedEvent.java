package com.fooddelivery.deliveryservice;

public class DriverAssignedEvent {
    public String eventId;
    public String eventType;
    public String timestamp;
    public String version;
    public Payload payload;

    public static class Payload {
        public String orderId;
        public String driverId;
        public int etaMinutes;
    }
}
