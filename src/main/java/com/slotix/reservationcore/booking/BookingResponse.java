package com.slotix.reservationcore.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(UUID id, Long bookingNumber, UUID companyId, UUID resourceId, UUID customerUserId,
                              Instant startAt, Instant endAt, BookingStatus status, String timezone,
                              String notes, Instant cancelledAt, String cancellationReason) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getBookingNumber(), booking.getCompanyId(),
            booking.getResourceId(), booking.getCustomerUserId(), booking.getStartAt(), booking.getEndAt(),
            booking.getStatus(), booking.getTimezone(), booking.getNotes(), booking.getCancelledAt(),
            booking.getCancellationReason());
    }
}
