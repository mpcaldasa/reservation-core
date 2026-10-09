package com.slotix.reservationcore.policy;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReplaceResourcePolicyRequest(@NotNull UUID policyId) {
}
