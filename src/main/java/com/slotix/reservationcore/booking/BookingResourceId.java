package com.slotix.reservationcore.booking;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record BookingResourceId(UUID bookingId, UUID resourceId) implements Serializable {
}
