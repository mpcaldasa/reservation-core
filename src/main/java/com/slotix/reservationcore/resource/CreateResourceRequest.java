package com.slotix.reservationcore.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateResourceRequest(
    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must not exceed 150 characters")
    String name,

    @Size(max = 2_000, message = "description must not exceed 2000 characters")
    String description,

    @NotNull(message = "resourceType is required")
    ResourceType resourceType,

    @Positive(message = "capacity must be greater than zero")
    int capacity,

    @NotNull(message = "visibility is required")
    ResourceVisibility visibility
) {
}
