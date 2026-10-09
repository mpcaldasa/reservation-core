package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.audit.AuditService;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PlatformBootstrapService {
    private final JdbcTemplate jdbc;
    private final PlatformUserRoleRepository roles;
    private final UserProvisioningService provisioning;
    private final Validator validator;
    private final AuditService audit;

    public PlatformBootstrapService(JdbcTemplate jdbc, PlatformUserRoleRepository roles,
                                    UserProvisioningService provisioning, Validator validator, AuditService audit) {
        this.jdbc = jdbc;
        this.roles = roles;
        this.provisioning = provisioning;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional
    public Optional<UUID> initialize(PlatformBootstrapProperties properties) {
        jdbc.execute("SELECT pg_advisory_xact_lock(836019771000)");
        if (roles.existsByRole(PlatformRole.PLATFORM_ADMIN)) {
            return Optional.empty();
        }
        NewUserRequest request = new NewUserRequest(properties.email(), properties.password(), properties.fullName());
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("Invalid platform administrator bootstrap configuration");
        }
        User user = provisioning.create(request);
        roles.saveAndFlush(PlatformUserRole.create(user.getId(), PlatformRole.PLATFORM_ADMIN));
        audit.record(null, user.getId(), "PLATFORM_ADMIN_BOOTSTRAPPED", "USER", user.getId(), null,
            "{\"role\":\"PLATFORM_ADMIN\"}");
        return Optional.of(user.getId());
    }
}
