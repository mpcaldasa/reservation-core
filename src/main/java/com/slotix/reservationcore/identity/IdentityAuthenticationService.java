package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.AuthenticatedPrincipal;
import com.slotix.reservationcore.common.JwtService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IdentityAuthenticationService {
    private final UserRepository users;
    private final CompanyMembershipRepository memberships;
    private final PlatformUserRoleRepository platformRoles;
    private final CompanyRepository companies;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final IdentityRateLimiter rateLimiter;

    public IdentityAuthenticationService(UserRepository users, CompanyMembershipRepository memberships,
                                          PlatformUserRoleRepository platformRoles, CompanyRepository companies,
                                          PasswordEncoder passwords, JwtService jwt, IdentityRateLimiter rateLimiter) {
        this.users = users;
        this.memberships = memberships;
        this.platformRoles = platformRoles;
        this.companies = companies;
        this.passwords = passwords;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
    }

    public LoginResponse login(LoginRequest request) {
        User user = authenticate(request.email(), request.password());
        AuthenticatedIdentity identity = resolve(user, request.companyId()).orElseThrow(InvalidCredentialsException::new);
        return LoginResponse.of(jwt.generateToken(user.getId(), request.companyId(), identity.roles(), user.getCredentialVersion()));
    }

    public LoginResponse platformLogin(PlatformLoginRequest request) {
        User user = authenticate(request.email(), request.password());
        AuthenticatedIdentity identity = resolve(user, null).orElseThrow(InvalidCredentialsException::new);
        return LoginResponse.of(jwt.generateToken(user.getId(), null, identity.roles(), user.getCredentialVersion()));
    }

    public Optional<AuthenticatedIdentity> resolve(UUID userId, UUID companyId) {
        return resolve(userId, companyId, 0);
    }

    public Optional<AuthenticatedIdentity> resolve(UUID userId, UUID companyId, int credentialVersion) {
        return users.findByIdAndDeletedAtIsNull(userId).filter(User::isActive)
            .filter(user -> user.getCredentialVersion() == credentialVersion)
            .flatMap(user -> resolve(user, companyId));
    }

    private Optional<AuthenticatedIdentity> resolve(User user, UUID companyId) {
        List<String> roles = new ArrayList<>(platformRoles.findByUserId(user.getId()).stream()
            .map(assignment -> assignment.getRole().name()).toList());
        if (companyId == null) {
            if (roles.isEmpty()) {
                return Optional.empty();
            }
        } else {
            if (companies.findById(companyId).filter(Company::isActive).isEmpty()) {
                return Optional.empty();
            }
            var membership = memberships.findByCompanyIdAndUserIdAndDeletedAtIsNull(companyId, user.getId())
                .filter(CompanyMembership::isActive);
            if (membership.isEmpty()) {
                return Optional.empty();
            }
            roles.addAll(membership.get().getRoles().stream().map(Enum::name).toList());
        }
        return Optional.of(new AuthenticatedIdentity(new AuthenticatedPrincipal(user.getId(), companyId),
            roles.stream().distinct().sorted().toList()));
    }

    private User authenticate(String email, String password) {
        rateLimiter.acquire("login", email, 20, 300);
        User user = users.findByEmailAndDeletedAtIsNull(email.trim().toLowerCase(Locale.ROOT))
            .filter(User::isActive).orElseThrow(InvalidCredentialsException::new);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72 || !passwords.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
