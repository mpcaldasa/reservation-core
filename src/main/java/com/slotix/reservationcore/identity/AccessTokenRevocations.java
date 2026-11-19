package com.slotix.reservationcore.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenRevocations {
    private final JdbcTemplate jdbc;

    public AccessTokenRevocations(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isRevoked(String tokenId) {
        if (tokenId == null) return true;
        UUID id = UUID.fromString(tokenId);
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM revoked_access_tokens WHERE token_id = ?)", Boolean.class, id));
    }

    public void revoke(String tokenId, Instant expiresAt) {
        jdbc.update("INSERT INTO revoked_access_tokens(token_id, expires_at) VALUES (?, ?) ON CONFLICT DO NOTHING",
            UUID.fromString(tokenId), Timestamp.from(expiresAt));
    }

    @Scheduled(fixedDelayString = "${app.jwt.revocation-cleanup-ms:60000}")
    public void removeExpired() {
        jdbc.update("DELETE FROM revoked_access_tokens WHERE expires_at < now()");
    }
}
