package com.fooddelivery.deliveryservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryServiceTest {

    private DeliveryRepository repo;
    private DeliveryKafkaProducer producer;
    private DeliveryService service;

    @BeforeEach
    void setUp() {
        repo     = mock(DeliveryRepository.class);
        producer = mock(DeliveryKafkaProducer.class);
        service  = new DeliveryService(repo, producer);
    }

    // ── createFromEvent ───────────────────────────────────────────────────────

    @Test
    void createFromEvent_savesDeliveryWithAssignedStatus() {
        var event = makeEvent("order-1", "driver-1", 25);
        when(repo.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.createFromEvent(event);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(result.getOrderId()).isEqualTo("order-1");
        assertThat(result.getEtaMinutes()).isEqualTo(25);
        verify(repo).save(any());
    }

    @Test
    void createFromEvent_idempotent_doesNotSaveAgain() {
        var existing = delivery("order-1", "driver-1", DeliveryStatus.ASSIGNED);
        when(repo.findByOrderId("order-1")).thenReturn(Optional.of(existing));

        service.createFromEvent(makeEvent("order-1", "driver-1", 25));

        verify(repo, never()).save(any());
    }

    // ── advanceStatus ─────────────────────────────────────────────────────────

    @Test
    void advanceStatus_assignedToPickedUp_succeeds() {
        var d = delivery("order-1", "driver-1", DeliveryStatus.ASSIGNED);
        when(repo.findByOrderId("order-1")).thenReturn(Optional.of(d));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.advanceStatus("order-1", DeliveryStatus.PICKED_UP);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        verifyNoInteractions(producer);
    }

    @Test
    void advanceStatus_inTransitToDelivered_publishesEvent() {
        var d = delivery("order-1", "driver-1", DeliveryStatus.IN_TRANSIT);
        when(repo.findByOrderId("order-1")).thenReturn(Optional.of(d));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.advanceStatus("order-1", DeliveryStatus.DELIVERED);

        var captor = ArgumentCaptor.forClass(OrderDeliveredEvent.class);
        verify(producer).publishOrderDelivered(captor.capture());
        assertThat(captor.getValue().payload.orderId).isEqualTo("order-1");
        assertThat(captor.getValue().payload.driverId).isEqualTo("driver-1");
    }

    @Test
    void advanceStatus_invalidTransition_throwsException() {
        var d = delivery("order-1", "driver-1", DeliveryStatus.ASSIGNED);
        when(repo.findByOrderId("order-1")).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.advanceStatus("order-1", DeliveryStatus.DELIVERED))
            .isInstanceOf(DeliveryService.InvalidStatusTransitionException.class);
    }

    @Test
    void advanceStatus_deliveryNotFound_throwsException() {
        when(repo.findByOrderId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advanceStatus("missing", DeliveryStatus.PICKED_UP))
            .isInstanceOf(DeliveryService.DeliveryNotFoundException.class);
    }

    @Test
    void advanceStatus_deliveredIsTerminal_throwsException() {
        var d = delivery("order-1", "driver-1", DeliveryStatus.DELIVERED);
        when(repo.findByOrderId("order-1")).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.advanceStatus("order-1", DeliveryStatus.PICKED_UP))
            .isInstanceOf(DeliveryService.InvalidStatusTransitionException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DriverAssignedEvent makeEvent(String orderId, String driverId, int eta) {
        var e = new DriverAssignedEvent();
        e.eventId   = "evt-1";
        e.eventType = "driver-assigned";
        e.timestamp = "2024-01-01T00:00:00Z";
        e.version   = "1";
        e.payload   = new DriverAssignedEvent.Payload();
        e.payload.orderId     = orderId;
        e.payload.driverId    = driverId;
        e.payload.etaMinutes  = eta;
        return e;
    }

    private Delivery delivery(String orderId, String driverId, DeliveryStatus status) {
        var d = new Delivery();
        d.setId(java.util.UUID.randomUUID().toString());
        d.setOrderId(orderId);
        d.setDriverId(driverId);
        d.setStatus(status);
        return d;
    }
}
