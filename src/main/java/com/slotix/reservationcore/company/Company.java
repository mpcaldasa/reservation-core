package com.slotix.reservationcore.company;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "contact_email", nullable = false, columnDefinition = "citext")
    private String contactEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Company() {
        // Required by JPA/Hibernate.
    }

    public static Company create(String legalName, String displayName, String slug, String contactEmail) {
        Company company = new Company();
        company.legalName = legalName;
        company.displayName = displayName;
        company.slug = slug;
        company.status = "PENDING";
        company.timezone = "America/Bogota";
        company.currencyCode = "COP";
        company.contactEmail = contactEmail;
        company.createdAt = Instant.now();
        company.updatedAt = Instant.now();
        return company;
    }

    // Getters only. State changes will be added with explicit domain operations.

    public UUID getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSlug() {
        return slug;
    }

    public String getStatus() {
        return status;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getContactEmail() {
        return contactEmail;
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

    public boolean isActive() {
        return "ACTIVE".equals(status) && deletedAt == null;
    }

    public void activate() {
        if (deletedAt != null) {
            throw new IllegalStateException("A deleted company cannot be activated");
        }
        status = "ACTIVE";
        updatedAt = Instant.now();
    }
}
