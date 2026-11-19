package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordResetRequest(@NotBlank @Size(max = 128) String token,
                                          @NotBlank @Size(min = 8, max = 72) String password) {
    @Override
    public String toString() {
        return "ConfirmPasswordResetRequest[token=[REDACTED], password=[REDACTED]]";
    }
}
