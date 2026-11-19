package com.slotix.reservationcore.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
            || secret.startsWith("slotix-dev-secret") || secret.chars().distinct().count() < 16) {
            throw new IllegalArgumentException("A random JWT signing secret of at least 32 bytes is required");
        }
        if (expirationMinutes < 1 || expirationMinutes > 60) {
            throw new IllegalArgumentException("JWT expiration must be between 1 and 60 minutes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(UUID userId, UUID companyId, List<String> roles) {
        return generateToken(userId, companyId, roles, 0);
    }

    public String generateToken(UUID userId, UUID companyId, List<String> roles, int credentialVersion) {
        Instant now = Instant.now();

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer("slotix")
            .audience().add("slotix-api").and()
            .subject(userId.toString())
            .claim("companyId", companyId == null ? null : companyId.toString())
            .claim("roles", roles)
            .claim("credentialVersion", credentialVersion)
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
            .signWith(key)
            .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .requireIssuer("slotix")
            .requireAudience("slotix-api")
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
