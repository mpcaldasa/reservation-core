package com.slotix.reservationcore.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordResetToken() {}

    public static PasswordResetToken create(UUID userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.status = "PENDING";
        token.expiresAt = expiresAt;
        token.createdAt = createdAt;
        return token;
    }

    public void markUsed(Instant occurredAt) {
        if (!"PENDING".equals(status)) throw new IllegalStateException("Only pending password reset tokens can be used");
        status = "USED";
        usedAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
}
