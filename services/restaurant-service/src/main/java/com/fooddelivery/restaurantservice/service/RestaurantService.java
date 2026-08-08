package com.fooddelivery.restaurantservice.service;

import com.fooddelivery.restaurantservice.domain.Restaurant;
import com.fooddelivery.restaurantservice.kafka.OrderEventPublisher;
import com.fooddelivery.restaurantservice.kafka.dto.OrderAcceptedEvent;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.web.CreateRestaurantRequest;
import com.fooddelivery.restaurantservice.web.RestaurantResponse;
import com.fooddelivery.restaurantservice.web.UpdateRestaurantRequest;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);

    private final RestaurantRepository repository;
    private final OrderEventPublisher  publisher;

    public RestaurantService(RestaurantRepository repository, OrderEventPublisher publisher) {
        this.repository = repository;
        this.publisher  = publisher;
    }

    @Transactional
    public RestaurantResponse create(CreateRestaurantRequest req) {
        var restaurant = new Restaurant(UUID.randomUUID().toString(), req.name(), req.avgPrepTimeMinutes());
        repository.save(restaurant);
        MDC.put("restaurantId", restaurant.getId());
        try {
            log.info("Restaurant created name={}", restaurant.getName());
        } finally {
            MDC.remove("restaurantId");
        }
        return RestaurantResponse.from(restaurant);
    }

    @Transactional(readOnly = true)
    public RestaurantResponse get(String id) {
        return RestaurantResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> list() {
        return repository.findAll().stream().map(RestaurantResponse::from).toList();
    }

    @Transactional
    public RestaurantResponse update(String id, UpdateRestaurantRequest req) {
        Restaurant r = find(id);
        r.updateDetails(req.name(), req.avgPrepTimeMinutes());
        repository.save(r);
        return RestaurantResponse.from(r);
    }

    @Transactional
    public void delete(String id) {
        repository.delete(find(id));
    }

    /**
     * Called by OrderEventConsumer when an order-created event arrives.
     * Looks up the restaurant's avgPrepTimeMinutes and publishes order-accepted.
     * If the restaurant is closed or not found, the order is silently dropped
     * (a real implementation would publish order-rejected — added in a later phase).
     */
    @Transactional(readOnly = true)
    public void acceptOrder(String orderId, String restaurantId) {
        MDC.put("orderId",      orderId);
        MDC.put("restaurantId", restaurantId);
        try {
            Restaurant restaurant = repository.findById(restaurantId).orElse(null);
            if (restaurant == null) {
                log.warn("Restaurant not found — dropping order");
                return;
            }
            if (!restaurant.isOpen()) {
                log.warn("Restaurant is closed — dropping order");
                return;
            }
            int prepTime = restaurant.getAvgPrepTimeMinutes();
            log.info("Accepting order prepTimeMinutes={}", prepTime);
            publisher.publishOrderAccepted(new OrderAcceptedEvent(orderId, restaurantId, prepTime));
        } finally {
            MDC.remove("orderId");
            MDC.remove("restaurantId");
        }
    }

    private Restaurant find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant not found: " + id));
    }
}
