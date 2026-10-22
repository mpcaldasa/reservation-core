package com.slotix.reservationcore.booking;

public record CancelBookingRequest(@jakarta.validation.constraints.Size(max = 2000) String reason) {
}
