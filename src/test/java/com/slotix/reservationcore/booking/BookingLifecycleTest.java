package com.slotix.reservationcore.booking;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingLifecycleTest {
    private static final Instant START = Instant.parse("2026-10-10T14:00:00Z");
    private static final Instant END = START.plusSeconds(1800);
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void checkInIncludesTheStartAndExcludesTheEnd() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        assertThrows(BookingTransitionException.class, () -> booking.checkIn(ACTOR, START.minusNanos(1)));
        assertThrows(BookingTransitionException.class, () -> booking.checkIn(ACTOR, END));
        assertTrue(booking.checkIn(ACTOR, START));
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
    }

    @Test
    void repeatedActionsPreserveTheirOriginalActorAndTime() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        booking.checkIn(ACTOR, START);
        assertFalse(booking.checkIn(UUID.randomUUID(), END.plusSeconds(1)));
        assertEquals(START, booking.getCheckedInAt());
        assertEquals(ACTOR, booking.getCheckedInBy());

        Instant completion = START.plusSeconds(60);
        assertTrue(booking.complete(ACTOR, completion));
        assertFalse(booking.complete(UUID.randomUUID(), completion.plusSeconds(60)));
        assertEquals(completion, booking.getCompletedAt());
        assertEquals(ACTOR, booking.getCompletedBy());
    }

    @Test
    void completionRequiresCheckInAndMonotonicTime() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        assertThrows(BookingTransitionException.class, () -> booking.complete(ACTOR, START));
        booking.checkIn(ACTOR, START);
        assertThrows(BookingTransitionException.class, () -> booking.complete(ACTOR, START.minusNanos(1)));
        assertTrue(booking.complete(ACTOR, START));
        assertThrows(BookingTransitionException.class, () -> booking.checkIn(ACTOR, START));
    }

    @Test
    void pendingRejectedAndCancelledBookingsCannotCheckIn() {
        Booking pending = booking(BookingStatus.PENDING);
        assertThrows(BookingTransitionException.class, () -> pending.checkIn(ACTOR, START));
        pending.decide(BookingStatus.REJECTED);
        assertThrows(BookingTransitionException.class, () -> pending.checkIn(ACTOR, START));

        Booking cancelled = booking(BookingStatus.CONFIRMED);
        cancelled.cancel(ACTOR, "Customer request");
        assertThrows(BookingTransitionException.class, () -> cancelled.checkIn(ACTOR, START));
    }

    @Test
    void checkedInBookingsCannotBeCancelled() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        booking.checkIn(ACTOR, START);
        assertThrows(BookingTransitionException.class, () -> booking.cancel(ACTOR, "Invalid cancellation"));
    }

    private Booking booking(BookingStatus status) {
        return Booking.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), START, END,
            status, "America/Bogota", null);
    }
}
