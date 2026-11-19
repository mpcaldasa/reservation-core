package com.slotix.reservationcore.observability;

import com.slotix.reservationcore.booking.BookingStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SlotixMetrics {
    private final MeterRegistry registry;

    public SlotixMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void bookingCreated(BookingStatus status) {
        registry.counter("slotix.booking.created", "status", status.name()).increment();
    }

    public void bookingTransitioned(BookingStatus status) {
        registry.counter("slotix.booking.transitioned", "status", status.name()).increment();
    }

    public void notificationDelivery(String channel, String result) {
        registry.counter("slotix.notification.delivery", "channel", channel, "result", result).increment();
    }
}
