package com.slotix.reservationcore.booking;

public class BookingTransitionException extends IllegalStateException {
    public BookingTransitionException(String message) {
        super(message);
    }
}
