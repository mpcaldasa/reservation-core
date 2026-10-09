package com.slotix.reservationcore.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
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

    public boolean activate(Instant updatedAt) {
        requireNotDeleted();
        if (status == ResourceStatus.ACTIVE) return false;
        status = ResourceStatus.ACTIVE;
        this.updatedAt = updatedAt;
        return true;
    }

    public boolean deactivate(Instant occurredAt) {
        requireNotDeleted();
        if (status == ResourceStatus.INACTIVE) return false;
        status = ResourceStatus.INACTIVE;
        updatedAt = occurredAt;
        return true;
    }

    public boolean softDelete(Instant occurredAt) {
        if (deletedAt != null) return false;
        deletedAt = occurredAt;
        status = ResourceStatus.INACTIVE;
        updatedAt = occurredAt;
        return true;
    }

    public boolean updateDetails(String name, String description, ResourceType resourceType, int capacity,
                                 ResourceVisibility visibility, Instant occurredAt) {
        requireNotDeleted();
        boolean changed = !Objects.equals(this.name, name) || !Objects.equals(this.description, description)
            || this.resourceType != resourceType || this.capacity != capacity || this.visibility != visibility;
        if (!changed) return false;
        this.name = name;
        this.description = description;
        this.resourceType = resourceType;
        this.capacity = capacity;
        this.visibility = visibility;
        updatedAt = occurredAt;
        return true;
    }

    private void requireNotDeleted() {
        if (deletedAt != null) throw new IllegalStateException("A deleted resource cannot be changed");
    }

    public ResourceVisibility getVisibility() {
        return visibility;
    }

    public boolean activate() {
        return activate(Instant.now());
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
