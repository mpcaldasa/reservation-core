package com.slotix.reservationcore.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resources")
public class Resource {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceVisibility visibility;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Resource() {
    }

    public static Resource create(
        UUID companyId,
        String name,
        String description,
        ResourceType resourceType,
        int capacity,
        ResourceVisibility visibility
    ) {
        Resource resource = new Resource();
        resource.companyId = companyId;
        resource.name = name;
        resource.description = description;
        resource.resourceType = resourceType;
        resource.capacity = capacity;
        resource.status = ResourceStatus.DRAFT;
        resource.visibility = visibility;
        resource.createdAt = Instant.now();
        resource.updatedAt = Instant.now();
        return resource;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public int getCapacity() {
        return capacity;
    }

    public ResourceStatus getStatus() {
        return status;
    }

    public void activate() {
        if (deletedAt != null) throw new IllegalStateException("A deleted resource cannot be activated");
        status = ResourceStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public ResourceVisibility getVisibility() {
        return visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
