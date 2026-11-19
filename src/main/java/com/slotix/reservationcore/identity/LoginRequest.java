package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LoginRequest(
    @NotNull(message = "companyId is required")
    UUID companyId,

    @NotBlank(message = "email is required")
    @jakarta.validation.constraints.Size(max = 320) String email,

    @NotBlank(message = "password is required")
    @jakarta.validation.constraints.Size(max = 72) String password
) {
    @Override
    public String toString() {
        return "LoginRequest[companyId=" + companyId + ", email=" + email + ", password=[REDACTED]]";
    }
}
