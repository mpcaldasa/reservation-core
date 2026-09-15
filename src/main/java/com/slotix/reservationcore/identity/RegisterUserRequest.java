package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record RegisterUserRequest(

    @NotBlank(message = "email es obligatorio")
    @Email(message = "email debe ser válido")
    String email,

    @NotBlank(message = "password es obligatorio")
    @Size(min = 8, message = "password debe tener al menos 8 caracteres")
    String password,

    @NotBlank(message = "fullName es obligatorio")
    String fullName,

    @NotNull(message = "companyId es obligatorio")
    UUID companyId,

    @NotEmpty(message = "roles debe tener al menos un rol")
    Set<MembershipRole> roles
) {
}
