package com.slotix.reservationcore.policy;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CloseResourcePolicyAssignmentRequest(@NotNull Instant effectiveTo) {
}
