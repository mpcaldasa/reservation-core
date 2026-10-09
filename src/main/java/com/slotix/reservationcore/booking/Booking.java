package com.slotix.reservationcore.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id @GeneratedValue private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "booking_number", insertable = false, updatable = false) private Long bookingNumber;
    @Column(name = "customer_user_id", nullable = false) private UUID customerUserId;
    @Column(name = "resource_id", nullable = false) private UUID resourceId;
    @Column(name = "start_at", nullable = false) private Instant startAt;
    @Column(name = "end_at", nullable = false) private Instant endAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private BookingStatus status;
    @Column(nullable = false) private String timezone;
    private String notes;
    @Column(name = "cancellation_reason") private String cancellationReason;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancelled_by") private UUID cancelledBy;
    @Column(name = "checked_in_at") private Instant checkedInAt;
    @Column(name = "checked_in_by") private UUID checkedInBy;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completed_by") private UUID completedBy;
    @Column(name = "no_show_at") private Instant noShowAt;
    @Column(name = "no_show_by") private UUID noShowBy;
    @Version private int version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Booking() {}

    public static Booking create(UUID companyId, UUID customerUserId, UUID resourceId, Instant startAt,
                                 Instant endAt, BookingStatus status, String timezone, String notes) {
        if (status != BookingStatus.PENDING && status != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Bookings must start pending or confirmed");
        }
        Booking booking = new Booking();
        booking.companyId = companyId;
        booking.customerUserId = customerUserId;
        booking.resourceId = resourceId;
        booking.startAt = startAt;
        booking.endAt = endAt;
        booking.status = status;
        booking.timezone = timezone;
        booking.notes = notes;
        booking.createdAt = Instant.now();
        booking.updatedAt = booking.createdAt;
        return booking;
    }

    public void cancel(UUID actor, String reason) {
        if (status != BookingStatus.PENDING && status != BookingStatus.CONFIRMED) {
            throw new BookingTransitionException("Only pending or confirmed bookings can be cancelled");
        }
        status = BookingStatus.CANCELLED;
        cancelledAt = Instant.now();
        cancelledBy = actor;
        cancellationReason = reason;
        updatedAt = cancelledAt;
    }

    public void decide(BookingStatus decision) {
        if (status != BookingStatus.PENDING || (decision != BookingStatus.CONFIRMED && decision != BookingStatus.REJECTED)) {
            throw new IllegalStateException("Only pending bookings can be approved or rejected");
        }
        status = decision;
        updatedAt = Instant.now();
    }

    public boolean checkIn(UUID actor, Instant occurredAt) {
        if (status == BookingStatus.CHECKED_IN) {
            return false;
        }
        if (status != BookingStatus.CONFIRMED) {
            throw new BookingTransitionException("Only confirmed bookings can be checked in");
        }
        if (occurredAt.isBefore(startAt) || !occurredAt.isBefore(endAt)) {
            throw new BookingTransitionException("Check-in must occur within the booking interval");
        }
        status = BookingStatus.CHECKED_IN;
        checkedInAt = occurredAt;
        checkedInBy = actor;
        updatedAt = occurredAt;
        return true;
    }

    public boolean complete(UUID actor, Instant occurredAt) {
        if (status == BookingStatus.COMPLETED) {
            return false;
        }
        if (status != BookingStatus.CHECKED_IN || checkedInAt == null) {
            throw new BookingTransitionException("Only checked-in bookings can be completed");
        }
        if (occurredAt.isBefore(checkedInAt)) {
            throw new BookingTransitionException("Completion cannot precede check-in");
        }
        status = BookingStatus.COMPLETED;
        completedAt = occurredAt;
        completedBy = actor;
        updatedAt = occurredAt;
        return true;
    }

    public void reschedule(Instant newStartAt, Instant newEndAt, Instant updatedAt) {
        if (status != BookingStatus.PENDING && status != BookingStatus.CONFIRMED) {
            throw new BookingTransitionException("Only pending or confirmed bookings can be rescheduled");
        }
        if (newStartAt == null || newEndAt == null || !newEndAt.isAfter(newStartAt)) {
            throw new BookingTransitionException("The rescheduled interval is invalid");
        }
        startAt = newStartAt;
        endAt = newEndAt;
        this.updatedAt = updatedAt;
    }

    public void expire(Instant expiredAt) {
        if (status != BookingStatus.PENDING) {
            throw new BookingTransitionException("Only pending bookings can expire");
        }
        status = BookingStatus.EXPIRED;
        updatedAt = expiredAt;
    }

    public boolean markNoShow(UUID actor, Instant occurredAt) {
        if (status == BookingStatus.NO_SHOW) {
            return false;
        }
        if (status != BookingStatus.CONFIRMED) {
            throw new BookingTransitionException("Only confirmed bookings can be marked as a no-show");
        }
        if (occurredAt.isBefore(endAt)) {
            throw new BookingTransitionException("A booking can be marked as a no-show after its end time");
        }
        status = BookingStatus.NO_SHOW;
        noShowAt = occurredAt;
        noShowBy = actor;
        updatedAt = occurredAt;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public Long getBookingNumber() { return bookingNumber; }
    public UUID getCustomerUserId() { return customerUserId; }
    public UUID getResourceId() { return resourceId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public BookingStatus getStatus() { return status; }
    public String getTimezone() { return timezone; }
    public String getNotes() { return notes; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public UUID getCheckedInBy() { return checkedInBy; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCompletedBy() { return completedBy; }
    public Instant getNoShowAt() { return noShowAt; }
    public UUID getNoShowBy() { return noShowBy; }
}
