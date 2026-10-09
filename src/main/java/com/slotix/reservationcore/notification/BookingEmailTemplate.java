package com.slotix.reservationcore.notification;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class BookingEmailTemplate {
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (VV)", Locale.ROOT);

    public EmailMessage render(NotificationDelivery delivery) {
        String action = switch (delivery.eventType()) {
            case "BOOKING_CREATED" -> "created";
            case "BOOKING_CONFIRMED" -> "confirmed";
            case "BOOKING_REJECTED" -> "rejected";
            case "BOOKING_CANCELLED" -> "cancelled";
            case "BOOKING_CHECKED_IN" -> "checked in";
            case "BOOKING_COMPLETED" -> "completed";
            case "BOOKING_RESCHEDULED" -> "rescheduled";
            case "BOOKING_EXPIRED" -> "expired";
            case "BOOKING_NO_SHOW" -> "marked as a no-show";
            default -> throw new IllegalArgumentException("Unsupported booking notification event");
        };
        ZoneId zone = ZoneId.of(delivery.timezone());
        String subject = "%s | Booking #%d %s".formatted(delivery.companyName(), delivery.bookingNumber(), action);
        String body = """
            Your booking #%d at %s was %s.
            Start: %s
            End: %s
            """.formatted(delivery.bookingNumber(), delivery.companyName(), action,
            TIME_FORMAT.format(delivery.startAt().atZone(zone)), TIME_FORMAT.format(delivery.endAt().atZone(zone)));
        return new EmailMessage(delivery.recipient(), subject, body);
    }
}
