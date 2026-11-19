package com.slotix.reservationcore.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(

    @NotBlank(message = "legalName is required")
    @Size(max = 200)
    String legalName,

    @NotBlank(message = "displayName is required")
    @Size(max = 150)
    String displayName,

    @NotBlank(message = "slug is required")
    @Size(max = 80)
    @Pattern(regexp = "[a-z0-9]++(?:-[a-z0-9]++)*+", message = "slug may contain only lowercase letters, numbers, and single hyphens")
    String slug,

    @NotBlank(message = "contactEmail is required")
    @Email(message = "contactEmail must be a valid email address")
    @Size(max = 320)
    String contactEmail
) {
}
