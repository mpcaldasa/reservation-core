package com.slotix.reservationcore.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCompanyRequest(

    @NotBlank(message = "legalName es obligatorio")
    String legalName,

    @NotBlank(message = "displayName es obligatorio")
    String displayName,

    @NotBlank(message = "slug es obligatorio")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "slug solo puede contener minúsculas, números y guiones")
    String slug,

    @NotBlank(message = "contactEmail es obligatorio")
    @Email(message = "contactEmail debe ser un correo válido")
    String contactEmail
) {
}
