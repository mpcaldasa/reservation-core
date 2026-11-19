package com.slotix.reservationcore.identity;

import java.time.Duration;
import java.util.Optional;

public interface IdentityEmailDeliveryStore {
    Optional<IdentityEmailDelivery> claimNext(int maxAttempts, Duration leaseDuration);
    boolean markSent(IdentityEmailDelivery delivery);
    boolean markFailed(IdentityEmailDelivery delivery, String errorCode, Duration retryDelay);
}
