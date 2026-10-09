package com.slotix.reservationcore.availability;

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
@Table(name = "resource_blocks")
public class ResourceBlock {
    @Id @GeneratedValue private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "resource_id", nullable = false) private UUID resourceId;
    @Column(name = "start_at", nullable = false) private Instant startAt;
    @Column(name = "end_at", nullable = false) private Instant endAt;
    @Column(nullable = false) private String reason;
    @Enumerated(EnumType.STRING) @Column(name = "block_type", nullable = false) private ResourceBlockType blockType;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected ResourceBlock() {
    }

    public static ResourceBlock create(UUID companyId, UUID resourceId, Instant startAt, Instant endAt,
                                       String reason, ResourceBlockType blockType) {
        ResourceBlock block = new ResourceBlock();
        block.companyId = companyId;
        block.resourceId = resourceId;
        block.startAt = startAt;
        block.endAt = endAt;
        block.reason = reason;
        block.blockType = blockType;
        block.createdAt = Instant.now();
        block.updatedAt = block.createdAt;
        return block;
    }

    public boolean update(Instant startAt, Instant endAt, String reason, ResourceBlockType blockType,
                          Instant updatedAt) {
        if (deletedAt != null) throw new IllegalStateException("A deleted resource block cannot be changed");
        boolean changed = !this.startAt.equals(startAt) || !this.endAt.equals(endAt)
            || !Objects.equals(this.reason, reason) || this.blockType != blockType;
        if (!changed) return false;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
        this.blockType = blockType;
        this.updatedAt = updatedAt;
        return true;
    }

    public boolean softDelete(Instant deletedAt) {
        if (this.deletedAt != null) return false;
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getResourceId() { return resourceId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public String getReason() { return reason; }
    public ResourceBlockType getBlockType() { return blockType; }
    public Instant getDeletedAt() { return deletedAt; }
}
