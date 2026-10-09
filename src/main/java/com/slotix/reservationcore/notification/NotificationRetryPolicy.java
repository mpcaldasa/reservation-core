package com.slotix.reservationcore.notification;

import java.time.Duration;

public class NotificationRetryPolicy {
    private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);
    private static final int MAX_EXPONENT = 4;

    public Duration delayAfter(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("Attempt must be positive");
        }
        return INITIAL_DELAY.multipliedBy(1L << Math.min(attempt - 1, MAX_EXPONENT));
    }
}
