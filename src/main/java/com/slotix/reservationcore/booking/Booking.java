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
    @Version private int version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Booking() {}

    public static Booking create(UUID companyId, UUID customerUserId, UUID resourceId, Instant startAt,
                                 Instant endAt, BookingStatus status, String timezone, String notes) {
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
}
