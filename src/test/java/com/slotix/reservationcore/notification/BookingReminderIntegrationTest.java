package com.slotix.reservationcore.notification;

import com.slotix.reservationcore.identity.IdentityIntegrationTestSupport;
import com.slotix.reservationcore.booking.Booking;
import com.slotix.reservationcore.booking.BookingRepository;
import com.slotix.reservationcore.booking.BookingStatus;
import com.slotix.reservationcore.resource.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingReminderIntegrationTest extends IdentityIntegrationTestSupport {
    @Autowired BookingReminderWorker reminders;
    @Autowired NotificationQueueWorker queue;
    @Autowired NotificationDeliveryStore deliveries;
    @Autowired ResourceRepository resources;
    @Autowired BookingRepository bookings;

    @Test
    void settingsRequireAdministratorAndConfiguredEmailDelivery() throws Exception {
        var fixture = fixture();
        String path = "/api/v1/companies/" + fixture.company().getId() + "/reminders";
        mvc.perform(get(path).header("Authorization", fixture.adminToken())).andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get(path+"/history").header("Authorization", fixture.adminToken())).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(path+"/history").param("size","51").header("Authorization", fixture.adminToken())).andExpect(status().isBadRequest());
        mvc.perform(get(path+"/history").header("Authorization", fixture.customerToken())).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", fixture.customerToken()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true,\"minutesBefore\":60}")).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false,\"minutesBefore\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put(path).header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true,\"minutesBefore\":60}")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void concurrentSchedulersDeduplicateAndCancelledRemindersAreSkipped() throws Exception {
        var fixture = fixture();
        var resource = resources.save(Resource.create(fixture.company().getId(), "Meeting room", null,
            ResourceType.SPACE, 1, ResourceVisibility.MEMBERS));
        var start = Instant.now().plusSeconds(1800);
        var booking = bookings.saveAndFlush(Booking.create(fixture.company().getId(), fixture.customer().getId(), resource.getId(),
            start, start.plusSeconds(1800), BookingStatus.CONFIRMED, "America/Bogota", null));
        jdbc.update("INSERT INTO company_reminder_settings(company_id,enabled,minutes_before) VALUES (?,true,60)", fixture.company().getId());
        CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); reminders.enqueueDueReminders(); return true; });
            var second = executor.submit(() -> { gate.await(); reminders.enqueueDueReminders(); return true; });
            gate.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        }
        reminders.enqueueDueReminders();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='BOOKING_REMINDER'", Integer.class));
        jdbc.update("UPDATE company_reminder_settings SET minutes_before=120 WHERE company_id=?", fixture.company().getId());
        reminders.enqueueDueReminders();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='BOOKING_REMINDER'", Integer.class));
        queue.stagePendingDeliveries();
        UUID delivery = jdbc.queryForObject("SELECT id FROM notification_deliveries WHERE booking_id=?", UUID.class, booking.getId());
        jdbc.update("UPDATE bookings SET status='CANCELLED' WHERE id=?", booking.getId());
        assertTrue(deliveries.claimNext(5, Duration.ofMinutes(2)).isEmpty());
        assertEquals("SKIPPED", jdbc.queryForObject("SELECT status FROM notification_deliveries WHERE id=?", String.class, delivery));
        mvc.perform(get("/api/v1/companies/"+fixture.company().getId()+"/reminders/history").header("Authorization",fixture.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].customerName").value("customer"))
            .andExpect(jsonPath("$.items[0].status").value("SKIPPED"));
    }

    @Test
    void reschedulingCreatesANewReminderAndInvalidatesTheOldOccurrence() {
        var fixture = fixture();
        var resource = resources.save(Resource.create(fixture.company().getId(), "Court", null,
            ResourceType.SPACE, 1, ResourceVisibility.MEMBERS));
        var start = Instant.now().plusSeconds(1800);
        var booking = bookings.saveAndFlush(Booking.create(fixture.company().getId(), fixture.customer().getId(), resource.getId(),
            start, start.plusSeconds(1800), BookingStatus.CONFIRMED, "America/Bogota", null));
        jdbc.update("INSERT INTO company_reminder_settings(company_id,enabled,minutes_before) VALUES (?,true,60)", fixture.company().getId());
        reminders.enqueueDueReminders(); queue.stagePendingDeliveries();
        jdbc.update("UPDATE bookings SET start_at=start_at+interval '5 minutes',end_at=end_at+interval '5 minutes' WHERE id=?", booking.getId());
        reminders.enqueueDueReminders(); queue.stagePendingDeliveries();
        var delivery = deliveries.claimNext(5, Duration.ofMinutes(2)).orElseThrow();
        assertEquals("BOOKING_REMINDER", delivery.eventType());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_deliveries WHERE status='SKIPPED'", Integer.class));
        assertTrue(deliveries.markSent(delivery));
        assertTrue(deliveries.claimNext(5, Duration.ofMinutes(2)).isEmpty());
    }
}
