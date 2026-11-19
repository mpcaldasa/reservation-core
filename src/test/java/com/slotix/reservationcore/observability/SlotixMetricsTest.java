package com.slotix.reservationcore.observability;

import com.slotix.reservationcore.booking.BookingStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotixMetricsTest {
    @Test
    void recordsBusinessCountersWithBoundedDimensions() {
        var registry = new SimpleMeterRegistry();
        var metrics = new SlotixMetrics(registry);

        metrics.bookingCreated(BookingStatus.CONFIRMED);
        metrics.bookingTransitioned(BookingStatus.COMPLETED);
        metrics.notificationDelivery("identity", "failed");

        assertThat(registry.get("slotix.booking.created").tag("status", "CONFIRMED").counter().count()).isEqualTo(1);
        assertThat(registry.get("slotix.booking.transitioned").tag("status", "COMPLETED").counter().count()).isEqualTo(1);
        assertThat(registry.get("slotix.notification.delivery").tag("channel", "identity")
            .tag("result", "failed").counter().count()).isEqualTo(1);
    }
}
