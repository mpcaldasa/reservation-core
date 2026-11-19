package com.slotix.reservationcore.notification;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationRetryPolicyTest {
    private final NotificationRetryPolicy policy = new NotificationRetryPolicy();

    @Test
    void exponentiallyDelaysRetriesAndCapsTheDelay() {
        assertEquals(Duration.ofMinutes(1), policy.delayAfter(1));
        assertEquals(Duration.ofMinutes(2), policy.delayAfter(2));
        assertEquals(Duration.ofMinutes(4), policy.delayAfter(3));
        assertEquals(Duration.ofMinutes(8), policy.delayAfter(4));
        assertEquals(Duration.ofMinutes(16), policy.delayAfter(5));
        assertEquals(Duration.ofMinutes(16), policy.delayAfter(Integer.MAX_VALUE));
    }

    @Test
    void rejectsNonpositiveAttemptNumbers() {
        assertThrows(IllegalArgumentException.class, () -> policy.delayAfter(0));
    }
}
