package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CompanyInvitationService {
    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final CompanyRepository companies;
    private final CompanyInvitationRepository invitations;
    private final UserRepository users;
    private final CompanyMembershipRepository memberships;
    private final UserProvisioningService provisioning;
    private final TenantAccessService access;
    private final MembershipAuditService membershipAudit;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean emailEnabled;

    public CompanyInvitationService(CompanyRepository companies, CompanyInvitationRepository invitations,
                                    UserRepository users, CompanyMembershipRepository memberships,
                                    UserProvisioningService provisioning, TenantAccessService access,
                                    MembershipAuditService membershipAudit, AuditService audit, JdbcTemplate jdbc,
                                    Clock clock, @Value("${app.notification.email.enabled:false}") boolean emailEnabled) {
        this.companies = companies;
        this.invitations = invitations;
        this.users = users;
        this.memberships = memberships;
        this.provisioning = provisioning;
        this.access = access;
        this.membershipAudit = membershipAudit;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.emailEnabled = emailEnabled;
    }

    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public CompanyInvitationResponse invite(UUID companyId, CreateCompanyInvitationRequest request) {
        if (!emailEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.EMAIL_DELIVERY_UNAVAILABLE,
                "Email delivery is not enabled; the invitation was not created");
        }
        lockActiveCompany(companyId);
        String email = normalizeEmail(request.email());
        if (users.findByEmailAndDeletedAtIsNull(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.USER_ALREADY_EXISTS,
                "The email address already has an account; add that user directly");
        }
        Instant now = now();
        invitations.expirePending(companyId, email, now);
        if (invitations.existsByCompanyIdAndInvitedEmailAndStatusAndExpiresAtAfter(companyId, email, "PENDING", now)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.INVITATION_ALREADY_EXISTS,
                "A pending invitation already exists for this email address");
        }

        String token = IdentityToken.generate();
        CompanyInvitation invitation = invitations.saveAndFlush(CompanyInvitation.create(companyId, email,
            access.currentUserId(), IdentityToken.hash(token), request.roles(), now.plus(INVITATION_TTL), now));
        jdbc.update("UPDATE identity_email_deliveries SET token = NULL, status = 'FAILED', claim_token = NULL, "
            + "claim_until = NULL, error_code = 'INVITATION_EXPIRED' "
            + "WHERE aggregate_type = 'COMPANY_INVITATION' AND aggregate_id IN "
            + "(SELECT id FROM company_invitations WHERE company_id = ? AND invited_email = ? "
            + "AND status = 'EXPIRED') AND token IS NOT NULL", companyId, email);
        jdbc.update("INSERT INTO identity_email_deliveries(aggregate_type, aggregate_id, recipient, token) "
            + "VALUES ('COMPANY_INVITATION', ?, ?, ?)", invitation.getId(), email, token);
        audit.record(companyId, access.currentUserId(), "COMPANY_MEMBER_INVITED", "COMPANY_INVITATION",
            invitation.getId(), null, "{\"email\":\"%s\",\"status\":\"PENDING\"}".formatted(email));
        return CompanyInvitationResponse.from(invitation);
    }

    public UserResponse accept(AcceptCompanyInvitationRequest request) {
        String suppliedToken = request.token().trim();
        if (suppliedToken.length() > 128) throw invitationNotFound();
        String tokenHash = IdentityToken.hash(suppliedToken);
        CompanyInvitation candidate = invitations.findByTokenHash(tokenHash)
            .orElseThrow(CompanyInvitationService::invitationNotFound);
        Company company = companies.lockById(candidate.getCompanyId()).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "The company is not active and cannot accept invitations"));
        CompanyInvitation invitation = invitations.lockByTokenHash(tokenHash)
            .orElseThrow(CompanyInvitationService::invitationNotFound);
        Instant now = now();
        if ("ACCEPTED".equals(invitation.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.INVITATION_ALREADY_ACCEPTED,
                "This invitation has already been accepted");
        }
        if (!"PENDING".equals(invitation.getStatus())) throw invitationNotFound();
        if (!invitation.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.GONE, ApiErrorCode.INVITATION_EXPIRED,
                "This invitation has expired; ask a company administrator to send another");
        }
        User user = provisioning.create(new NewUserRequest(invitation.getInvitedEmail(), request.password(), request.fullName()));
        CompanyMembership membership = memberships.saveAndFlush(CompanyMembership.create(company.getId(), user.getId(), invitation.getRoles()));
        invitation.accept(now);
        jdbc.update("UPDATE identity_email_deliveries SET token = NULL, status = 'FAILED', claim_token = NULL, "
            + "claim_until = NULL, error_code = 'INVITATION_ACCEPTED' WHERE aggregate_type = 'COMPANY_INVITATION' "
            + "AND aggregate_id = ?", invitation.getId());
        membershipAudit.record(membership, null, "COMPANY_MEMBER_INVITATION_ACCEPTED", null);
        audit.record(company.getId(), null, "COMPANY_INVITATION_ACCEPTED", "COMPANY_INVITATION", invitation.getId(),
            "{\"status\":\"PENDING\"}", "{\"status\":\"ACCEPTED\"}");
        return UserResponse.from(user, membership);
    }

    private void lockActiveCompany(UUID companyId) {
        access.requireActiveMembership(companyId);
        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "Company is not active"));
        access.requireCompanyRole(companyId, MembershipRole.COMPANY_ADMIN);
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private static ApiException invitationNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.INVITATION_NOT_FOUND,
            "The invitation token is invalid or no longer available");
    }
}
