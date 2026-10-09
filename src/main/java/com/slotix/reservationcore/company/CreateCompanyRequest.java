package com.slotix.reservationcore.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCompanyRequest(

    @NotBlank(message = "legalName is required")
    String legalName,

    @NotBlank(message = "displayName is required")
    String displayName,

    @NotBlank(message = "slug is required")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "slug may contain only lowercase letters, numbers, and hyphens")
    String slug,

    @NotBlank(message = "contactEmail is required")
    @Email(message = "contactEmail must be a valid email address")
    String contactEmail
) {
}
