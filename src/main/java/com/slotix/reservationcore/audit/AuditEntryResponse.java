package com.slotix.reservationcore.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(UUID id, UUID companyId, UUID actorUserId, String action, String entityType,
                                 UUID entityId, String beforeData, String afterData, String correlationId,
                                 Instant createdAt) {}
