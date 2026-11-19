package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.AuthenticatedPrincipal;
import com.slotix.reservationcore.common.JwtService;
import com.slotix.reservationcore.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final IdentityAuthenticationService identities;
    private final JwtService jwt;
    private final IdentityRateLimiter limits;
    private final AuditService audit;

    public CurrentUserController(UserRepository users, JdbcTemplate jdbc, IdentityAuthenticationService identities,
                                 JwtService jwt, IdentityRateLimiter limits, AuditService audit) {
        this.users=users; this.jdbc=jdbc; this.identities=identities; this.jwt=jwt; this.limits=limits; this.audit=audit;
    }

    public record Workspace(UUID companyId, String companyName, String timezone) {}
    public record Profile(String fullName, String email, UUID companyId, List<Workspace> workspaces) {}
    public record CompanySelection(@NotNull UUID companyId) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Profile get(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        User user = users.findByIdAndDeletedAtIsNull(principal.userId()).filter(User::isActive)
            .orElseThrow(() -> new AccessDeniedException("The user is inactive"));
        var workspaces = jdbc.query("""
            SELECT c.id,c.display_name,c.timezone FROM companies c JOIN company_memberships m ON m.company_id=c.id
            WHERE m.user_id=? AND m.status='ACTIVE' AND m.deleted_at IS NULL AND c.status='ACTIVE' AND c.deleted_at IS NULL
            ORDER BY lower(c.display_name),c.id
            """, (row,index) -> new Workspace(row.getObject(1,UUID.class),row.getString(2),row.getString(3)), user.getId());
        return new Profile(user.getFullName(), user.getEmail(), principal.companyId(), workspaces);
    }

    @PostMapping("/company")
    @Transactional
    public LoginResponse select(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @Valid @RequestBody CompanySelection selection) {
        limits.acquire("company-switch", principal.userId().toString(), 20, 60);
        var identity = identities.resolve(principal.userId(), selection.companyId(), principal.credentialVersion())
            .orElseThrow(() -> new AccessDeniedException("The user has no active membership in this company"));
        audit.record(selection.companyId(), principal.userId(), "COMPANY_CONTEXT_SELECTED", "COMPANY", selection.companyId(), null, null);
        return LoginResponse.of(jwt.generateToken(principal.userId(), selection.companyId(), identity.roles(), principal.credentialVersion()));
    }
}
