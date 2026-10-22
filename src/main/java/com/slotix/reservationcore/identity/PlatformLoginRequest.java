package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PlatformLoginRequest(@NotBlank @Email @jakarta.validation.constraints.Size(max = 320) String email,
                                   @NotBlank @jakarta.validation.constraints.Size(max = 72) String password) {
    @Override
    public String toString() {
        return "PlatformLoginRequest[email=" + email + ", password=[REDACTED]]";
    }
}
