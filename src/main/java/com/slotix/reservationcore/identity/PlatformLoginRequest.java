package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PlatformLoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    @Override
    public String toString() {
        return "PlatformLoginRequest[email=" + email + ", password=[REDACTED]]";
    }
}
