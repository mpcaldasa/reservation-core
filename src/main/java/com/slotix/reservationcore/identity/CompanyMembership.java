package com.slotix.reservationcore.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "company_memberships")
public class CompanyMembership {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String status;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "membership_roles",
        joinColumns = @JoinColumn(name = "membership_id")
    )
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<MembershipRole> roles = new HashSet<>();

    protected CompanyMembership() {
    }

    public static CompanyMembership create(UUID companyId, UUID userId, Set<MembershipRole> roles) {
        CompanyMembership membership = new CompanyMembership();
        membership.companyId = companyId;
        membership.userId = userId;
        membership.status = "ACTIVE";
        membership.joinedAt = Instant.now();
        membership.createdAt = Instant.now();
        membership.updatedAt = Instant.now();
        membership.roles = new HashSet<>(roles);
        return membership;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public Set<MembershipRole> getRoles() {
        return Set.copyOf(roles);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status) && deletedAt == null;
    }
}
