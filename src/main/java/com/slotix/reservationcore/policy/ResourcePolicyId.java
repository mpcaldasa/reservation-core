package com.slotix.reservationcore.policy;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Embeddable
public record ResourcePolicyId(UUID resourceId, Instant effectiveFrom) implements Serializable {
}
