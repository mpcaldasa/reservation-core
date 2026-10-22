package com.slotix.reservationcore.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationOutboxService {
    private final JdbcTemplate jdbc;

    public NotificationOutboxService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void enqueueBookingEvent(UUID companyId, UUID bookingId, String eventType) {
        String payload = "{\"bookingId\":\"" + bookingId + "\"}";
        jdbc.update("insert into outbox_events(company_id,aggregate_type,aggregate_id,event_type,payload) " +
                "values (?,'BOOKING',?,?,cast(? as jsonb))",
            companyId, bookingId, eventType, payload);
    }
}
