package com.slotix.reservationcore.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "credential_version", nullable = false)
    private int credentialVersion;

    protected User() {
        // Required by JPA/Hibernate.
    }

    public static User create(String email, String passwordHash, String fullName) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.status = "ACTIVE";
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        return user;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getStatus() {
        return status;
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

    public int getCredentialVersion() { return credentialVersion; }

    public boolean isActive() {
        return "ACTIVE".equals(status) && deletedAt == null;
    }

    public void changePasswordHash(String passwordHash, Instant occurredAt) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
        this.passwordHash = passwordHash;
        this.updatedAt = occurredAt;
        this.credentialVersion++;
    }

    public boolean deactivate(Instant occurredAt) {
        if (!isActive()) return false;
        status = "INACTIVE";
        updatedAt = occurredAt;
        credentialVersion++;
        return true;
    }

    public boolean reactivate(Instant occurredAt) {
        if (deletedAt != null || isActive()) return false;
        status = "ACTIVE";
        updatedAt = occurredAt;
        credentialVersion++;
        return true;
    }
}
