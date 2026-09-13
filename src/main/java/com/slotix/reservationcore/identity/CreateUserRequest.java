package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(

    UUID companyId,

    @NotBlank(message = "email es obligatorio")
    @Email(message = "email debe ser válido")
    String email,

    @NotBlank(message = "password es obligatorio")
    @Size(min = 8, message = "password debe tener al menos 8 caracteres")
    String password,

    @NotBlank(message = "fullName es obligatorio")
    String fullName,

    @NotBlank(message = "role es obligatorio")
    String role
) {
}
