package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@Transactional
public class PasswordResetService {
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean emailEnabled;
    private final IdentityTokenCipher cipher;
    private final IdentityRateLimiter rateLimiter;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                PasswordEncoder passwords, AuditService audit, JdbcTemplate jdbc, Clock clock,
                                @Value("${app.notification.email.enabled:false}") boolean emailEnabled,
                                IdentityTokenCipher cipher, IdentityRateLimiter rateLimiter) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.emailEnabled = emailEnabled;
        this.cipher = cipher;
        this.rateLimiter = rateLimiter;
    }

    public PasswordResetResponse request(PasswordResetRequest request) {
        rateLimiter.acquire("reset", request.email(), 5, 3600);
        if (!emailEnabled) return PasswordResetResponse.requested();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        users.lockByEmailAndDeletedAtIsNull(email).filter(User::isActive).ifPresent(user -> {
            if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), now().minus(REQUEST_WINDOW)) < MAX_REQUESTS_PER_WINDOW) {
                createReset(user);
            }
        });
        return PasswordResetResponse.requested();
    }

    public PasswordResetResponse confirm(ConfirmPasswordResetRequest request) {
        String suppliedToken = request.token().trim();
        if (suppliedToken.length() > 128) throw tokenNotFound();
        String tokenHash = IdentityToken.hash(suppliedToken);
        PasswordResetToken candidate = tokens.findByTokenHash(tokenHash).orElseThrow(PasswordResetService::tokenNotFound);
        User user = users.lockByIdAndDeletedAtIsNull(candidate.getUserId()).filter(User::isActive)
            .orElseThrow(PasswordResetService::tokenNotFound);
        PasswordResetToken resetToken = tokens.lockByTokenHash(tokenHash)
            .orElseThrow(PasswordResetService::tokenNotFound);
        if ("USED".equals(resetToken.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.PASSWORD_RESET_ALREADY_USED,
                "This password reset token has already been used");
        }
        if (!"PENDING".equals(resetToken.getStatus())) throw tokenNotFound();
        Instant now = now();
        if (!resetToken.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.GONE, ApiErrorCode.PASSWORD_RESET_EXPIRED,
                "This password reset token has expired; request another reset email");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.PASSWORD_BYTE_LIMIT_EXCEEDED,
                "Password must not exceed 72 UTF-8 bytes");
        }

        user.changePasswordHash(passwords.encode(request.password()), now);
        resetToken.markUsed(now);
        tokens.revokePendingForUser(user.getId());
        jdbc.update("UPDATE identity_email_deliveries SET token = NULL, status = 'FAILED', claim_token = NULL, "
            + "claim_until = NULL, error_code = 'PASSWORD_RESET_COMPLETED' WHERE aggregate_type = 'PASSWORD_RESET' "
            + "AND aggregate_id IN (SELECT id FROM password_reset_tokens WHERE user_id = ? AND status <> 'PENDING')",
            user.getId());
        audit.record(null, null, "PASSWORD_RESET_COMPLETED", "USER", user.getId(), null,
            "{\"credentialVersion\":%d}".formatted(user.getCredentialVersion()));
        return PasswordResetResponse.completed();
    }

    private void createReset(User user) {
        Instant now = now();
        tokens.revokePendingForUser(user.getId());
        jdbc.update("UPDATE identity_email_deliveries SET token = NULL, status = 'FAILED', claim_token = NULL, "
            + "claim_until = NULL, error_code = 'PASSWORD_RESET_REPLACED' WHERE aggregate_type = 'PASSWORD_RESET' "
            + "AND aggregate_id IN (SELECT id FROM password_reset_tokens WHERE user_id = ? AND status = 'REVOKED')",
            user.getId());
        String token = IdentityToken.generate();
        PasswordResetToken resetToken = tokens.saveAndFlush(PasswordResetToken.create(user.getId(),
            IdentityToken.hash(token), now.plus(TOKEN_TTL), now));
        jdbc.update("INSERT INTO identity_email_deliveries(aggregate_type, aggregate_id, recipient, token) "
            + "VALUES ('PASSWORD_RESET', ?, ?, ?)", resetToken.getId(), user.getEmail(), cipher.encrypt(token));
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private static ApiException tokenNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.PASSWORD_RESET_NOT_FOUND,
            "The password reset token is invalid or unavailable");
    }
}
