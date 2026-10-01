package com.slotix.reservationcore.policy;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resource_policies")
public class ResourcePolicy {
    @EmbeddedId
    private ResourcePolicyId id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    protected ResourcePolicy() {
    }

    public static ResourcePolicy create(UUID resourceId, UUID policyId, Instant from, Instant to) {
        ResourcePolicy assignment = new ResourcePolicy();
        assignment.id = new ResourcePolicyId(resourceId, from);
        assignment.policyId = policyId;
        assignment.effectiveTo = to;
        return assignment;
    }

    public ResourcePolicyId getId() { return id; }
    public UUID getPolicyId() { return policyId; }
    public Instant getEffectiveTo() { return effectiveTo; }

    public boolean covers(Instant start, Instant end) {
        return !start.isBefore(id.effectiveFrom()) && (effectiveTo == null || !end.isAfter(effectiveTo));
    }
}
