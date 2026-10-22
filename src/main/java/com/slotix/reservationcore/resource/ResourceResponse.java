package com.slotix.reservationcore.resource;

import java.time.Instant;
import java.util.UUID;

public record ResourceResponse(
    UUID id,
    UUID companyId,
    String name,
    String description,
    ResourceType resourceType,
    int capacity,
    ResourceStatus status,
    ResourceVisibility visibility,
    Instant createdAt,
    Instant updatedAt
) {
    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
            resource.getId(),
            resource.getCompanyId(),
            resource.getName(),
            resource.getDescription(),
            resource.getResourceType(),
            resource.getCapacity(),
            resource.getStatus(),
            resource.getVisibility(),
            resource.getCreatedAt(),
            resource.getUpdatedAt()
        );
    }
}
