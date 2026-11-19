package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformUserAdministrationService {
    private static final long PLATFORM_ADMIN_LOCK_KEY = 836019771000L;
    private final UserRepository users;
    private final PlatformUserRoleRepository platformRoles;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final TenantAccessService access;
    private final Clock clock;

    public PlatformUserAdministrationService(UserRepository users, PlatformUserRoleRepository platformRoles,
                                             JdbcTemplate jdbc, AuditService audit, TenantAccessService access,
                                             Clock clock) {
        this.users = users;
        this.platformRoles = platformRoles;
        this.jdbc = jdbc;
        this.audit = audit;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlatformUserPageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.PLATFORM_USER_PAGE_INVALID,
                "Page must be nonnegative and size must be between 1 and 100");
        }
        var result = users.findByDeletedAtIsNull(PageRequest.of(page, size, Sort.by("email", "id")));
        var ids = result.stream().map(User::getId).toList();
        Map<UUID, java.util.List<String>> rolesByUserId = platformRoles.findByUserIdIn(ids).stream()
            .collect(Collectors.groupingBy(PlatformUserRole::getUserId,
                Collectors.mapping(role -> role.getRole().name(), Collectors.collectingAndThen(Collectors.toList(),
                    roles -> roles.stream().sorted().toList()))));
        var items = result.stream().map(user -> new PlatformUserResponse(user.getId(), user.getEmail(),
            user.getFullName(), user.getStatus(), rolesByUserId.getOrDefault(user.getId(), java.util.List.of()),
            user.getCreatedAt(), user.getUpdatedAt())).toList();
        return new PlatformUserPageResponse(items, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages());
    }

    public PlatformUserResponse deactivate(UUID userId) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + PLATFORM_ADMIN_LOCK_KEY + ")");
        User user = find(userId);
        if (user.isActive() && platformRoles.existsByUserIdAndRole(userId, PlatformRole.PLATFORM_ADMIN)
            && platformRoles.countActivePlatformAdministrators() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.LAST_PLATFORM_ADMIN_REQUIRED,
                "The platform must retain at least one active platform administrator");
        }
        if (user.deactivate(now())) {
            revokePendingPasswordResets(userId);
            audit.record(null, access.currentUserId(), "PLATFORM_USER_DEACTIVATED", "USER", userId,
                "{\"status\":\"ACTIVE\"}", "{\"status\":\"INACTIVE\"}");
        }
        return response(user);
    }

    private void revokePendingPasswordResets(UUID userId) {
        jdbc.update("UPDATE password_reset_tokens SET status = 'REVOKED' WHERE user_id = ? AND status = 'PENDING'", userId);
        jdbc.update("UPDATE identity_email_deliveries SET token = NULL, status = 'FAILED', claim_token = NULL, "
            + "claim_until = NULL, error_code = 'ACCOUNT_DEACTIVATED' WHERE aggregate_type = 'PASSWORD_RESET' "
            + "AND aggregate_id IN (SELECT id FROM password_reset_tokens WHERE user_id = ? AND status = 'REVOKED')",
            userId);
    }

    public PlatformUserResponse reactivate(UUID userId) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + PLATFORM_ADMIN_LOCK_KEY + ")");
        User user = find(userId);
        if (user.reactivate(now())) {
            audit.record(null, access.currentUserId(), "PLATFORM_USER_REACTIVATED", "USER", userId,
                "{\"status\":\"INACTIVE\"}", "{\"status\":\"ACTIVE\"}");
        }
        return response(user);
    }

    public PlatformUserResponse grantPlatformAdmin(UUID userId) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + PLATFORM_ADMIN_LOCK_KEY + ")");
        User user = find(userId);
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.PLATFORM_ADMIN_REQUIRES_ACTIVE_USER,
                "Platform administrator privileges can only be granted to an active account");
        }
        if (!platformRoles.existsByUserIdAndRole(userId, PlatformRole.PLATFORM_ADMIN)) {
            platformRoles.saveAndFlush(PlatformUserRole.create(userId, PlatformRole.PLATFORM_ADMIN));
            audit.record(null, access.currentUserId(), "PLATFORM_ADMIN_ROLE_GRANTED", "USER", userId,
                null, "{\"role\":\"PLATFORM_ADMIN\"}");
        }
        return response(user);
    }

    public PlatformUserResponse revokePlatformAdmin(UUID userId) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + PLATFORM_ADMIN_LOCK_KEY + ")");
        User user = find(userId);
        PlatformUserRole assignment = platformRoles.findByUserId(userId).stream()
            .filter(role -> role.getRole() == PlatformRole.PLATFORM_ADMIN).findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.PLATFORM_ROLE_NOT_FOUND,
                "The user does not have the platform administrator role"));
        if (user.isActive() && platformRoles.countActivePlatformAdministrators() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.LAST_PLATFORM_ADMIN_REQUIRED,
                "The platform must retain at least one active platform administrator");
        }
        platformRoles.delete(assignment);
        audit.record(null, access.currentUserId(), "PLATFORM_ADMIN_ROLE_REVOKED", "USER", userId,
            "{\"role\":\"PLATFORM_ADMIN\"}", null);
        return response(user);
    }

    private User find(UUID userId) {
        return users.lockByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.PLATFORM_USER_NOT_FOUND,
                "User account not found"));
    }

    private PlatformUserResponse response(User user) {
        var roles = platformRoles.findByUserId(user.getId()).stream().map(PlatformUserRole::getRole)
            .map(Enum::name).sorted().toList();
        return new PlatformUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getStatus(), roles,
            user.getCreatedAt(), user.getUpdatedAt());
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
