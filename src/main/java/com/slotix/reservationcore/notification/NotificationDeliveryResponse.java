package com.slotix.reservationcore.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDeliveryResponse(UUID id, UUID companyId, UUID bookingId, String eventType,
                                          String status, int attempts, String errorCode,
                                          Instant nextAttemptAt, Instant createdAt, Instant sentAt) {}
