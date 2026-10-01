package com.slotix.reservationcore.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationQueueWorker {
    private final JdbcTemplate jdbc;

    public NotificationQueueWorker(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelayString = "${app.notification.queue-delay-ms:5000}")
    @Transactional
    public void stagePendingDeliveries() {
        List<UUID> eventIds = jdbc.query("select id from outbox_events where processed_at is null " +
                "order by occurred_at limit 25 for update skip locked",
            (row, index) -> row.getObject(1, UUID.class));
        for (UUID eventId : eventIds) {
            jdbc.update("insert into notification_deliveries(company_id,booking_id,outbox_event_id,channel,template,recipient,status) " +
                "select o.company_id,b.id,o.id,'EMAIL',o.event_type,u.email,'PENDING' " +
                "from outbox_events o join bookings b on b.id=o.aggregate_id " +
                "join users u on u.id=b.customer_user_id where o.id=? " +
                "on conflict (outbox_event_id) do nothing", eventId);
            jdbc.update("update outbox_events set processed_at=now(), attempts=attempts+1 where id=?", eventId);
        }
    }
}
