package com.slotix.reservationcore.notification;

import java.time.Duration;
import java.util.Optional;

public interface NotificationDeliveryStore {
    /** Commits exclusive ownership before returning the delivery to the caller. */
    Optional<NotificationDelivery> claimNext(int maxAttempts, Duration leaseDuration);

    /** Returns false if the claim has been replaced or already completed. */
    boolean markSent(NotificationDelivery delivery);

    /** Returns false if the claim has been replaced or already completed. */
    boolean markFailed(NotificationDelivery delivery, String errorCode, Duration retryDelay);
}
