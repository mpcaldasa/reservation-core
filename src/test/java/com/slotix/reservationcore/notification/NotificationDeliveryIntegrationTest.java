package com.slotix.reservationcore.notification;

import com.slotix.reservationcore.PostgresIntegrationTestSupport;
import com.slotix.reservationcore.booking.Booking;
import com.slotix.reservationcore.booking.BookingRepository;
import com.slotix.reservationcore.booking.BookingStatus;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.identity.UserRepository;
import com.slotix.reservationcore.resource.Resource;
import com.slotix.reservationcore.resource.ResourceRepository;
import com.slotix.reservationcore.resource.ResourceType;
import com.slotix.reservationcore.resource.ResourceVisibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
    "app.notification.email.enabled=true",
    "app.notification.email.from=booking@slotix.test",
    "spring.mail.host=localhost",
    "management.health.mail.enabled=false"
})
class NotificationDeliveryIntegrationTest extends PostgresIntegrationTestSupport {
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private CompanyRepository companies;
    @Autowired private UserRepository users;
    @Autowired private ResourceRepository resources;
    @Autowired private BookingRepository bookings;
    @Autowired private NotificationOutboxService outbox;
    @Autowired private NotificationQueueWorker queue;
    @Autowired private NotificationDeliveryStore store;
    @Autowired private EmailDeliveryWorker worker;
    @MockitoBean private JavaMailSender smtp;

    private UUID companyId;
    private UUID userId;
    private UUID otherCompanyId;

    @BeforeEach
    void clearQueue() {
        jdbc.update("DELETE FROM notification_deliveries");
        jdbc.update("DELETE FROM outbox_events");
    }

    @AfterEach
    void removeFixture() {
        clearQueue();
        if (companyId != null) {
            jdbc.update("DELETE FROM bookings WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM resources WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        if (otherCompanyId != null) {
            jdbc.update("DELETE FROM companies WHERE id = ?", otherCompanyId);
        }
    }

    @Test
    void retriesSmtpFailureAndRecordsTheSuccessfulAttempt() {
        UUID id = stageDelivery();
        queue.stagePendingDeliveries();
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM notification_deliveries", Long.class));
        doThrow(new MailSendException("SMTP unavailable")).when(smtp).send(any(SimpleMailMessage.class));

        worker.sendDueEmails();
        assertEquals("FAILED", status(id));
        assertEquals(1, attempts(id));
        assertEquals("SMTP_SEND_FAILED", jdbc.queryForObject(
            "SELECT error_message FROM notification_deliveries WHERE id = ?", String.class, id));
        worker.sendDueEmails();
        assertEquals(1, attempts(id));

        reset(smtp);
        jdbc.update("UPDATE notification_deliveries SET next_attempt_at = now() - interval '1 second' WHERE id = ?", id);
        worker.sendDueEmails();

        assertEquals("SENT", status(id));
        assertEquals(2, attempts(id));
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        assertEquals("booking@slotix.test", message.getValue().getFrom());
        assertEquals("customer@example.test", message.getValue().getTo()[0]);
        assertTrue(message.getValue().getSubject().contains("created"));
    }

    @Test
    void sendsOutsideTheCallerDatabaseTransaction() {
        UUID id = stageDelivery();
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(smtp).send(any(SimpleMailMessage.class));

        transactions.executeWithoutResult(ignored -> worker.sendDueEmails());

        assertEquals("SENT", status(id));
        verify(smtp).send(any(SimpleMailMessage.class));
    }

    @Test
    void finalSmtpFailureIsNotRetriedAgain() {
        UUID id = stageDelivery();
        jdbc.update("UPDATE notification_deliveries SET attempts = 4 WHERE id = ?", id);
        doThrow(new MailSendException("SMTP unavailable")).when(smtp).send(any(SimpleMailMessage.class));

        worker.sendDueEmails();
        jdbc.update("UPDATE notification_deliveries SET next_attempt_at = now() - interval '1 second' WHERE id = ?", id);
        worker.sendDueEmails();

        assertEquals("FAILED", status(id));
        assertEquals(5, attempts(id));
        verify(smtp, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void tenantMismatchDoesNotSilentlyProcessAnOutboxEvent() {
        UUID id = stageDelivery();
        UUID eventId = jdbc.queryForObject("SELECT outbox_event_id FROM notification_deliveries WHERE id = ?", UUID.class, id);
        otherCompanyId = companies.save(Company.create("Other Company", "Other Company",
            "other-" + UUID.randomUUID(), "other@example.test")).getId();
        jdbc.update("DELETE FROM notification_deliveries WHERE id = ?", id);
        jdbc.update("UPDATE outbox_events SET company_id = ?, processed_at = NULL WHERE id = ?", otherCompanyId, eventId);

        assertThrows(IllegalStateException.class, queue::stagePendingDeliveries);

        assertTrue(jdbc.queryForObject("SELECT processed_at IS NULL FROM outbox_events WHERE id = ?", Boolean.class, eventId));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM notification_deliveries", Long.class));
    }

    @Test
    void rejectsAcknowledgementsFromAReplacedClaim() {
        UUID id = stageDelivery();
        NotificationDelivery first = store.claimNext(5, LEASE_DURATION).orElseThrow();
        expireClaim(id);
        NotificationDelivery second = store.claimNext(5, LEASE_DURATION).orElseThrow();

        assertNotEquals(first.claimToken(), second.claimToken());
        assertEquals(2, second.attempt());
        assertFalse(store.markSent(first));
        assertFalse(store.markFailed(first, "STALE_FAILURE", Duration.ofMinutes(1)));
        assertEquals("SENDING", status(id));
        assertTrue(store.markSent(second));
        assertEquals("SENT", status(id));
    }

    @Test
    void expiredFinalAttemptBecomesTerminalFailure() {
        UUID id = stageDelivery();
        NotificationDelivery lastAttempt = store.claimNext(1, LEASE_DURATION).orElseThrow();
        expireClaim(id);

        assertTrue(store.claimNext(1, LEASE_DURATION).isEmpty());
        assertEquals("FAILED", status(id));
        assertEquals(1, attempts(id));
        assertFalse(store.markSent(lastAttempt));
    }

    @Test
    void concurrentWorkersCannotClaimTheSameDelivery() throws Exception {
        UUID id = stageDelivery();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimAfter(start));
            var second = executor.submit(() -> claimAfter(start));
            start.countDown();
            long successfulClaims = Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                .filter(Optional::isPresent).count();
            assertEquals(1, successfulClaims);
        }
        assertEquals(1, attempts(id));
    }

    private Optional<NotificationDelivery> claimAfter(CountDownLatch start) throws InterruptedException {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return store.claimNext(5, LEASE_DURATION);
    }

    private UUID stageDelivery() {
        UUID bookingId = transactions.execute(ignored -> {
            Company company = companies.save(Company.create("Notification Company", "North Court",
                "notification-" + UUID.randomUUID(), "contact@example.test"));
            companyId = company.getId();
            User user = users.save(User.create("customer@example.test", "unused", "Notification Customer"));
            userId = user.getId();
            Resource resource = resources.save(Resource.create(companyId, "Court", null,
                ResourceType.SPACE, 1, ResourceVisibility.MEMBERS));
            Booking booking = bookings.saveAndFlush(Booking.create(companyId, userId, resource.getId(),
                Instant.parse("2026-10-10T14:00:00Z"), Instant.parse("2026-10-10T14:30:00Z"),
                BookingStatus.CONFIRMED, "America/Bogota", null));
            outbox.enqueueBookingEvent(companyId, booking.getId(), "BOOKING_CREATED");
            return booking.getId();
        });
        queue.stagePendingDeliveries();
        return jdbc.queryForObject("SELECT id FROM notification_deliveries WHERE booking_id = ?", UUID.class, bookingId);
    }

    private void expireClaim(UUID id) {
        jdbc.update("UPDATE notification_deliveries SET claim_until = now() - interval '1 second' WHERE id = ?", id);
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM notification_deliveries WHERE id = ?", String.class, id);
    }

    private int attempts(UUID id) {
        return jdbc.queryForObject("SELECT attempts FROM notification_deliveries WHERE id = ?", Integer.class, id);
    }
}
