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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "company_invitations")
public class CompanyInvitation {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "invited_email", nullable = false, columnDefinition = "citext")
    private String invitedEmail;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_invitation_roles", joinColumns = @JoinColumn(name = "invitation_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<MembershipRole> roles = new HashSet<>();

    protected CompanyInvitation() {}

    public static CompanyInvitation create(UUID companyId, String email, UUID invitedBy, String tokenHash,
                                           Set<MembershipRole> roles, Instant expiresAt, Instant createdAt) {
        if (roles == null || roles.isEmpty() || roles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("An invitation must include at least one valid role");
        }
        CompanyInvitation invitation = new CompanyInvitation();
        invitation.companyId = companyId;
        invitation.invitedEmail = email;
        invitation.invitedBy = invitedBy;
        invitation.tokenHash = tokenHash;
        invitation.roles = new HashSet<>(roles);
        invitation.expiresAt = expiresAt;
        invitation.createdAt = createdAt;
        invitation.status = "PENDING";
        return invitation;
    }

    public boolean accept(Instant acceptedAt) {
        if (!"PENDING".equals(status)) return false;
        status = "ACCEPTED";
        this.acceptedAt = acceptedAt;
        return true;
    }

    public boolean expire() {
        if (!"PENDING".equals(status)) return false;
        status = "EXPIRED";
        return true;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getInvitedEmail() { return invitedEmail; }
    public UUID getInvitedBy() { return invitedBy; }
    public String getTokenHash() { return tokenHash; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Set<MembershipRole> getRoles() { return Set.copyOf(roles); }
}
