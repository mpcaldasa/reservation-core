package com.slotix.reservationcore.notification;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import com.slotix.reservationcore.observability.SlotixMetrics;
import com.slotix.reservationcore.observability.ScheduledJobObserver;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailDeliveryWorkerTest {
    @Test
    void exhaustedSmtpRetriesEmitASeparateBoundedMetric() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        EmailSender sender = mock(EmailSender.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SlotixMetrics metrics = new SlotixMetrics(registry);
        NotificationDelivery delivery = new NotificationDelivery(UUID.randomUUID(), UUID.randomUUID(), 2,
            "customer@example.test", "BOOKING_CREATED", 42, Instant.parse("2026-10-10T14:00:00Z"),
            Instant.parse("2026-10-10T14:30:00Z"), "America/Bogota", "North Court");
        when(store.claimNext(anyInt(), any())).thenReturn(Optional.of(delivery), Optional.empty());
        when(store.markFailed(delivery, "SMTP_SEND_FAILED", new NotificationRetryPolicy().delayAfter(2)))
            .thenReturn(true);
        doThrow(new EmailDeliveryException(new IllegalStateException("SMTP unavailable")))
            .when(sender).send(any(EmailMessage.class));
        EmailDeliveryWorker worker = new EmailDeliveryWorker(store, sender, new BookingEmailTemplate(),
            new NotificationRetryPolicy(), new NotificationEmailProperties("booking@slotix.test", 2, 10, 120),
            metrics, new ScheduledJobObserver(ObservationRegistry.create()));

        worker.sendDueEmails();

        org.junit.jupiter.api.Assertions.assertEquals(1.0,
            registry.get("slotix.notification.delivery").tag("channel", "booking").tag("result", "exhausted")
                .counter().count());
        verify(store).markFailed(delivery, "SMTP_SEND_FAILED", new NotificationRetryPolicy().delayAfter(2));
    }

    @Test
    void databaseAcknowledgementFailureDoesNotBecomeAnSmtpFailure() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        EmailSender sender = mock(EmailSender.class);
        NotificationDelivery delivery = new NotificationDelivery(UUID.randomUUID(), UUID.randomUUID(), 1,
            "customer@example.test", "BOOKING_CREATED", 42, Instant.parse("2026-10-10T14:00:00Z"),
            Instant.parse("2026-10-10T14:30:00Z"), "America/Bogota", "North Court");
        when(store.claimNext(anyInt(), any())).thenReturn(Optional.of(delivery));
        doThrow(new DataAccessResourceFailureException("Database unavailable")).when(store).markSent(delivery);
        EmailDeliveryWorker worker = new EmailDeliveryWorker(store, sender, new BookingEmailTemplate(),
            new NotificationRetryPolicy(), new NotificationEmailProperties("booking@slotix.test", 5, 10, 120),
            mock(SlotixMetrics.class), new ScheduledJobObserver(ObservationRegistry.create()));

        assertThrows(DataAccessResourceFailureException.class, worker::sendDueEmails);

        verify(sender).send(any(EmailMessage.class));
        verify(store, never()).markFailed(any(), anyString(), any());
    }
}
