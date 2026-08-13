  package com.fooddelivery.deliveryservice;

  import java.util.Map;
  import java.util.Set;

  public enum DeliveryStatus {
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED;

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED =
        Map.of(
            ASSIGNED, Set.of(PICKED_UP),
            PICKED_UP, Set.of(IN_TRANSIT),
            IN_TRANSIT, Set.of(DELIVERED),
            DELIVERED, Set.of());

    public boolean canTransitionTo(DeliveryStatus next) {
      return ALLOWED.get(this).contains(next);
    }
  }
