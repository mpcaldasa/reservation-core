package com.slotix.reservationcore.booking;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_resources")
public class BookingResource {
    @EmbeddedId private BookingResourceId id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "start_at", nullable = false) private Instant startAt;
    @Column(name = "end_at", nullable = false) private Instant endAt;
    @Column(nullable = false) private int units;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private BookingStatus status;
    @Column(name = "single_capacity", nullable = false) private boolean singleCapacity;

    protected BookingResource() {}

    public static BookingResource create(Booking booking, boolean singleCapacity) {
        BookingResource row = new BookingResource();
        row.id = new BookingResourceId(booking.getId(), booking.getResourceId());
        row.companyId = booking.getCompanyId();
        row.startAt = booking.getStartAt();
        row.endAt = booking.getEndAt();
        row.units = 1;
        row.status = booking.getStatus();
        row.singleCapacity = singleCapacity;
        return row;
    }

    public void cancel() { status = BookingStatus.CANCELLED; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public BookingResourceId getId() { return id; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
}
