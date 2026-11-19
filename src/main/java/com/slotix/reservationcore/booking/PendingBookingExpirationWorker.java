package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.notification.NotificationOutboxService;
import com.slotix.reservationcore.observability.ScheduledJobObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PendingBookingExpirationWorker {
    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookings;
    private final BookingResourceRepository allocations;
    private final AuditService audit;
    private final NotificationOutboxService notifications;
    private final Clock clock;
    private final Duration pendingTimeout;
    private final ScheduledJobObserver jobs;

    public PendingBookingExpirationWorker(BookingRepository bookings, BookingResourceRepository allocations,
                                          AuditService audit, NotificationOutboxService notifications, Clock clock,
                                          @Value("${app.booking.pending-timeout:PT30M}") Duration pendingTimeout,
                                          ScheduledJobObserver jobs) {
        if (pendingTimeout.isZero() || pendingTimeout.isNegative()) {
            throw new IllegalArgumentException("Pending booking timeout must be positive");
        }
        this.bookings = bookings;
        this.allocations = allocations;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.pendingTimeout = pendingTimeout;
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${app.booking.expiration.poll-interval-ms:60000}",
        initialDelayString = "${app.booking.expiration.initial-delay-ms:60000}")
    @Transactional
    public void expirePendingBookings() {
        jobs.observe("pending-booking-expiration", this::expireBatch);
    }

    private void expireBatch() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant cutoff = now.minus(pendingTimeout);
        for (Booking booking : bookings.lockExpiredPending(cutoff, PageRequest.of(0, BATCH_SIZE))) {
            expire(booking, now);
        }
    }

    private void expire(Booking booking, Instant expiredAt) {
        booking.expire(expiredAt);
        bookings.flush();

        BookingResource allocation = allocations.findByIdBookingIdAndIdResourceId(booking.getId(), booking.getResourceId())
            .orElseThrow(() -> new IllegalStateException("Pending booking allocation is missing: " + booking.getId()));
        if (allocation.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Pending booking allocation status is inconsistent: " + booking.getId());
        }
        allocation.setStatus(BookingStatus.EXPIRED);
        allocations.flush();

        audit.record(booking.getCompanyId(), null, "BOOKING_EXPIRED", "BOOKING", booking.getId(),
            "{\"status\":\"PENDING\"}", "{\"status\":\"EXPIRED\"}");
        notifications.enqueueBookingEvent(booking.getCompanyId(), booking.getId(), "BOOKING_EXPIRED");
    }
}
