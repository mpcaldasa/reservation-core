package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record RegisterUserRequest(

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    String email,

    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must contain at least 8 characters")
    String password,

    @NotBlank(message = "fullName is required")
    String fullName,

    @NotNull(message = "companyId is required")
    UUID companyId,

    @NotEmpty(message = "roles must contain at least one role")
    Set<MembershipRole> roles
) {
}
