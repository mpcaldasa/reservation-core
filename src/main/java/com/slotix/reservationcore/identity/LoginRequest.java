package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record LoginRequest(
    UUID companyId,

    @NotBlank(message = "email es obligatorio")
    String email,

    @NotBlank(message = "password es obligatorio")
    String password
) {
}
