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
    @Size(max = 320)
    String email,

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 72, message = "password must contain between 8 and 72 characters")
    String password,

    @NotBlank(message = "fullName is required")
    @Size(max = 200)
    String fullName,

    @NotNull(message = "companyId is required")
    UUID companyId,

    @NotEmpty(message = "roles must contain at least one role")
    Set<@NotNull MembershipRole> roles
) {
    @Override
    public String toString() {
        return "RegisterUserRequest[email=" + email + ", password=[REDACTED], fullName=" + fullName
            + ", companyId=" + companyId + ", roles=" + roles + "]";
    }
}
