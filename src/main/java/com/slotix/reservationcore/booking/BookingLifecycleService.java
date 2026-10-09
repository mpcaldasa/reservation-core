package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.notification.NotificationOutboxService;
import com.slotix.reservationcore.observability.SlotixMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER')")
public class BookingLifecycleService {
    private final BookingRepository bookings;
    private final BookingResourceRepository allocations;
    private final TenantAccessService access;
    private final AuditService audit;
    private final NotificationOutboxService notifications;
    private final Clock clock;
    private final SlotixMetrics metrics;

    public BookingLifecycleService(BookingRepository bookings, BookingResourceRepository allocations,
                                   TenantAccessService access, AuditService audit,
                                   NotificationOutboxService notifications, Clock clock, SlotixMetrics metrics) {
        this.bookings = bookings;
        this.allocations = allocations;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.metrics = metrics;
    }

    public BookingResponse checkIn(UUID companyId, UUID bookingId) {
        Booking booking = loadForUpdate(companyId, bookingId);
        BookingStatus previous = booking.getStatus();
        UUID actor = access.currentUserId();
        if (!booking.checkIn(actor, currentTime())) {
            return BookingResponse.from(booking);
        }
        return persistTransition(booking, previous, actor);
    }

    public BookingResponse complete(UUID companyId, UUID bookingId) {
        Booking booking = loadForUpdate(companyId, bookingId);
        BookingStatus previous = booking.getStatus();
        UUID actor = access.currentUserId();
        if (!booking.complete(actor, currentTime())) {
            return BookingResponse.from(booking);
        }
        return persistTransition(booking, previous, actor);
    }

    public BookingResponse markNoShow(UUID companyId, UUID bookingId) {
        Booking booking = loadForUpdate(companyId, bookingId);
        BookingStatus previous = booking.getStatus();
        UUID actor = access.currentUserId();
        if (!booking.markNoShow(actor, currentTime())) {
            return BookingResponse.from(booking);
        }
        return persistTransition(booking, previous, actor);
    }

    private Booking loadForUpdate(UUID companyId, UUID bookingId) {
        access.requireActiveMembership(companyId);
        return bookings.lockByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.BOOKING_NOT_FOUND,
                "The booking does not exist in this company"));
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private BookingResponse persistTransition(Booking booking, BookingStatus previous, UUID actor) {
        bookings.flush();
        BookingResource allocation = allocations.findByIdBookingIdAndIdResourceId(booking.getId(), booking.getResourceId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_ALLOCATION_NOT_FOUND,
                "The booking allocation record is missing"));
        if (allocation.getStatus() != previous) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_ALLOCATION_STATE_MISMATCH,
                "The booking and allocation statuses are inconsistent");
        }
        allocation.setStatus(booking.getStatus());
        allocations.flush();

        String event = "BOOKING_" + booking.getStatus();
        audit.record(booking.getCompanyId(), actor, event, "BOOKING", booking.getId(),
            "{\"status\":\"%s\"}".formatted(previous), "{\"status\":\"%s\"}".formatted(booking.getStatus()));
        notifications.enqueueBookingEvent(booking.getCompanyId(), booking.getId(), event);
        metrics.bookingTransitioned(booking.getStatus());
        return BookingResponse.from(booking);
    }
}
