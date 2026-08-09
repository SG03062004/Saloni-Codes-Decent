package com.fooddelivery.restaurantservice.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EventEnvelope<T>(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("eventType") String eventType,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("version") String version,
    @JsonProperty("payload") T payload) {
  public static <T> EventEnvelope<T> of(String eventId, String eventType, T payload) {
    return new EventEnvelope<>(eventId, eventType, Instant.now(), "1", payload);
  }
}
