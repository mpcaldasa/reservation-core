package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 200) String fullName) {
    @Override
    public String toString() {
        return "NewUserRequest[email=" + email + ", password=[REDACTED], fullName=" + fullName + "]";
    }
}
