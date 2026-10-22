package com.slotix.reservationcore.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDelivery(UUID id, UUID claimToken, int attempt, String recipient, String eventType,
                                   long bookingNumber, Instant startAt, Instant endAt, String timezone,
                                   String companyName) {}
