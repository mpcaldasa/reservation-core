package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record AddMembershipRequest(@NotNull UUID userId, @NotEmpty Set<@NotNull MembershipRole> roles) {}
