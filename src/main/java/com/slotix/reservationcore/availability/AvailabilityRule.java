package com.slotix.reservationcore.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "availability_rules")
public class AvailabilityRule {
    @Id @GeneratedValue private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "resource_id", nullable = false) private UUID resourceId;
    @Column(nullable = false) private short weekday;
    @Column(name = "start_local_time", nullable = false) private LocalTime startLocalTime;
    @Column(name = "end_local_time", nullable = false) private LocalTime endLocalTime;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected AvailabilityRule() {
    }

    public static AvailabilityRule create(UUID companyId, UUID resourceId, short weekday, LocalTime start,
                                          LocalTime end, LocalDate effectiveFrom, LocalDate effectiveTo) {
        AvailabilityRule rule = new AvailabilityRule();
        rule.companyId = companyId;
        rule.resourceId = resourceId;
        rule.weekday = weekday;
        rule.startLocalTime = start;
        rule.endLocalTime = end;
        rule.effectiveFrom = effectiveFrom;
        rule.effectiveTo = effectiveTo;
        rule.status = "ACTIVE";
        rule.createdAt = Instant.now();
        rule.updatedAt = rule.createdAt;
        return rule;
    }

    public boolean update(short weekday, LocalTime start, LocalTime end, LocalDate effectiveFrom,
                          LocalDate effectiveTo, Instant updatedAt) {
        if (deletedAt != null) throw new IllegalStateException("A deleted availability rule cannot be changed");
        boolean changed = this.weekday != weekday || !this.startLocalTime.equals(start) || !this.endLocalTime.equals(end)
            || !java.util.Objects.equals(this.effectiveFrom, effectiveFrom)
            || !java.util.Objects.equals(this.effectiveTo, effectiveTo);
        if (!changed) return false;
        this.weekday = weekday;
        this.startLocalTime = start;
        this.endLocalTime = end;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
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
    public short getWeekday() { return weekday; }
    public LocalTime getStartLocalTime() { return startLocalTime; }
    public LocalTime getEndLocalTime() { return endLocalTime; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public Instant getDeletedAt() { return deletedAt; }
}
