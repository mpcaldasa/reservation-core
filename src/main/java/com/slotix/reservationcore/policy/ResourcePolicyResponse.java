package com.slotix.reservationcore.policy;

import java.time.Instant;
import java.util.UUID;

public record ResourcePolicyResponse(UUID resourceId, UUID policyId, Instant effectiveFrom, Instant effectiveTo) {
    public static ResourcePolicyResponse from(ResourcePolicy assignment) {
        return new ResourcePolicyResponse(assignment.getId().resourceId(), assignment.getPolicyId(),
            assignment.getId().effectiveFrom(), assignment.getEffectiveTo());
    }
}
