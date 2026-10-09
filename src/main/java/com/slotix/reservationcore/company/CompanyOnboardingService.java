package com.slotix.reservationcore.company;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.identity.CompanyMembership;
import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.identity.InitialAdministratorRequest;
import com.slotix.reservationcore.identity.MembershipAuditService;
import com.slotix.reservationcore.identity.MembershipResponse;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.identity.UserProvisioningService;
import com.slotix.reservationcore.identity.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class CompanyOnboardingService {
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;
    private final UserRepository users;
    private final UserProvisioningService provisioning;
    private final TenantAccessService access;
    private final AuditService audit;
    private final MembershipAuditService membershipAudit;
    private final Clock clock;

    public CompanyOnboardingService(CompanyRepository companies, CompanyMembershipRepository memberships,
                                     UserRepository users, UserProvisioningService provisioning,
                                     TenantAccessService access, AuditService audit,
                                     MembershipAuditService membershipAudit, Clock clock) {
        this.companies = companies;
        this.memberships = memberships;
        this.users = users;
        this.provisioning = provisioning;
        this.access = access;
        this.audit = audit;
        this.membershipAudit = membershipAudit;
        this.clock = clock;
    }

    public CompanyOnboardingResponse onboard(CompanyOnboardingRequest request) {
        CreateCompanyRequest details = request.company();
        if (companies.findBySlug(details.slug()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.COMPANY_SLUG_ALREADY_EXISTS,
                "Company slug already exists");
        }
        Company company = Company.create(details.legalName().trim(), details.displayName().trim(), details.slug(),
            details.contactEmail().trim());
        company.activate();
        company = companies.saveAndFlush(company);
        User administrator = resolveAdministrator(request.administrator());
        CompanyMembership membership = memberships.saveAndFlush(CompanyMembership.create(company.getId(), administrator.getId(),
            Set.of(MembershipRole.COMPANY_ADMIN)));
        UUID actor = access.currentUserId();
        audit.record(company.getId(), actor, "COMPANY_ONBOARDED", "COMPANY", company.getId(), null, "{\"status\":\"ACTIVE\"}");
        membershipAudit.record(membership, actor, "COMPANY_INITIAL_ADMINISTRATOR_ASSIGNED", null);
        return new CompanyOnboardingResponse(CompanyResponse.from(company), MembershipResponse.from(membership, administrator));
    }

    public CompanyOnboardingResponse assignInitialAdministrator(UUID companyId, InitialAdministratorRequest request) {
        Company company = companies.lockById(companyId).filter(candidate -> candidate.getDeletedAt() == null)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COMPANY_NOT_FOUND,
                "Company not found"));
        if (!"PENDING".equals(company.getStatus()) && !company.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_ONBOARDING_STATE_INVALID,
                "Company cannot be onboarded from its current status");
        }
        if (memberships.countActiveAdministrators(companyId) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.COMPANY_ADMIN_ALREADY_ASSIGNED,
                "Company already has an active administrator");
        }
        User user = resolveAdministrator(request);
        var existing = memberships.findByCompanyIdAndUserIdAndDeletedAtIsNull(companyId, user.getId());
        CompanyMembership membership;
        String before = null;
        if (existing.isPresent()) {
            membership = existing.get();
            before = membershipAudit.snapshot(membership);
            var roles = new HashSet<>(membership.getRoles());
            roles.add(MembershipRole.COMPANY_ADMIN);
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            membership.replaceRoles(roles, now);
            membership.activate(now);
            memberships.flush();
        } else {
            membership = memberships.saveAndFlush(CompanyMembership.create(companyId, user.getId(), Set.of(MembershipRole.COMPANY_ADMIN)));
        }
        UUID actor = access.currentUserId();
        if (!company.isActive()) {
            company.activate();
            audit.record(companyId, actor, "COMPANY_ACTIVATED", "COMPANY", companyId,
                "{\"status\":\"PENDING\"}", "{\"status\":\"ACTIVE\"}");
        }
        membershipAudit.record(membership, actor, "COMPANY_INITIAL_ADMINISTRATOR_ASSIGNED", before);
        return new CompanyOnboardingResponse(CompanyResponse.from(company), MembershipResponse.from(membership, user));
    }

    private User resolveAdministrator(InitialAdministratorRequest request) {
        if (!request.isSingleAdministratorSpecified()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.ADMINISTRATOR_IDENTITY_INVALID,
                "Specify exactly one administrator identity");
        }
        if (request.existingUserId() != null) {
            return users.findByIdAndDeletedAtIsNull(request.existingUserId()).filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.ACTIVE_ADMINISTRATOR_NOT_FOUND,
                    "Active administrator user not found"));
        }
        return provisioning.create(request.newUser());
    }
}
