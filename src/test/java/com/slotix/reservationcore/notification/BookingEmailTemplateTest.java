package com.slotix.reservationcore.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingEmailTemplateTest {
    private final BookingEmailTemplate template = new BookingEmailTemplate();

    @Test
    void rendersSupportedEventsInBookingTimezone() {
        assertEmail("BOOKING_CREATED", "created");
        assertEmail("BOOKING_CONFIRMED", "confirmed");
        assertEmail("BOOKING_REJECTED", "rejected");
        assertEmail("BOOKING_CANCELLED", "cancelled");
        assertEmail("BOOKING_CHECKED_IN", "checked in");
        assertEmail("BOOKING_COMPLETED", "completed");
    }

    @Test
    void rejectsUnsupportedEvents() {
        assertThrows(IllegalArgumentException.class, () -> template.render(delivery("UNKNOWN")));
    }

    private void assertEmail(String eventType, String action) {
        EmailMessage message = template.render(delivery(eventType));
        assertEquals("customer@example.test", message.recipient());
        assertEquals("North Court | Booking #42 " + action, message.subject());
        assertEquals("Your booking #42 at North Court was " + action + ".\n" +
            "Start: 2026-10-10 09:00 (America/Bogota)\n" +
            "End: 2026-10-10 09:30 (America/Bogota)\n", message.body());
    }

    private NotificationDelivery delivery(String eventType) {
        return new NotificationDelivery(UUID.randomUUID(), UUID.randomUUID(), 1, "customer@example.test",
            eventType, 42, Instant.parse("2026-10-10T14:00:00Z"), Instant.parse("2026-10-10T14:30:00Z"),
            "America/Bogota", "North Court");
    }
}
