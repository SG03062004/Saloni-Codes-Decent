package com.fooddelivery.orderservice.service;

import com.fooddelivery.orderservice.domain.Order;
import com.fooddelivery.orderservice.domain.OrderItem;
import com.fooddelivery.orderservice.kafka.OrderEventPublisher;
import com.fooddelivery.orderservice.kafka.dto.OrderCreatedEvent;
import com.fooddelivery.orderservice.repository.OrderItemRepository;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.web.CreateOrderRequest;
import com.fooddelivery.orderservice.web.OrderResponse;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository     orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventPublisher publisher;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        OrderEventPublisher publisher) {
        this.orderRepository     = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.publisher           = publisher;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        String orderId = UUID.randomUUID().toString();
        MDC.put("orderId", orderId);
        try {
            int totalCents = req.items().stream()
                    .mapToInt(i -> i.quantity() * i.unitPriceCents())
                    .sum();

            var order = new Order(
                    orderId,
                    req.customerId(),
                    req.restaurantId(),
                    totalCents,
                    BigDecimal.valueOf(req.deliveryAddress().lat()),
                    BigDecimal.valueOf(req.deliveryAddress().lng()),
                    req.deliveryAddress().street(),
                    req.deliveryAddress().city()
            );
            orderRepository.save(order);

            List<OrderItem> items = req.items().stream()
                    .map(i -> new OrderItem(
                            UUID.randomUUID().toString(),
                            orderId,
                            i.menuItemId(),
                            i.quantity(),
                            i.unitPriceCents()))
                    .toList();
            orderItemRepository.saveAll(items);
            order.setItems(items);

            log.info("Order created status={} totalCents={}", order.getStatus(), totalCents);

            publisher.publishOrderCreated(toEvent(order, items, req));

            return OrderResponse.from(order, items);
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        MDC.put("orderId", orderId);
        try {
            Order order = findOrder(orderId);
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            order.setItems(items);
            return OrderResponse.from(order, items);
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void markAccepted(String orderId) {
        MDC.put("orderId", orderId);
        try {
            Order order = findOrder(orderId);
            order.accept();
            orderRepository.save(order);
            log.info("Order status updated to ACCEPTED");
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void markDriverAssigned(String orderId, int etaMinutes) {
        MDC.put("orderId", orderId);
        try {
            Order order = findOrder(orderId);
            order.assignDriver(etaMinutes);
            orderRepository.save(order);
            log.info("Order status updated to DRIVER_ASSIGNED etaMinutes={}", etaMinutes);
        } finally {
            MDC.remove("orderId");
        }
    }

    private Order findOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
    }

    private OrderCreatedEvent toEvent(Order order, List<OrderItem> items, CreateOrderRequest req) {
        var eventItems = items.stream()
                .map(i -> new OrderCreatedEvent.ItemDto(i.getMenuItemId(), i.getQuantity(), i.getUnitPriceCents()))
                .toList();
        var addr = new OrderCreatedEvent.AddressDto(
                req.deliveryAddress().lat(),
                req.deliveryAddress().lng(),
                req.deliveryAddress().street(),
                req.deliveryAddress().city());
        return new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), order.getRestaurantId(),
                eventItems, addr, order.getTotalCents(), order.getCreatedAt());
    }
}
