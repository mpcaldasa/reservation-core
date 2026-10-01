package com.slotix.reservationcore.policy;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AssignResourcePolicyRequest(@NotNull UUID policyId, @NotNull Instant effectiveFrom, Instant effectiveTo) {
}
