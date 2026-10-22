package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateCompanyInvitationRequest(@NotNull @Email @Size(max = 320) String email,
                                             @NotEmpty Set<@NotNull MembershipRole> roles) {}
