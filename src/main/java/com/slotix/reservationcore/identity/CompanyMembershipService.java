package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class CompanyMembershipService {
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;
    private final UserRepository users;
    private final UserProvisioningService provisioning;
    private final TenantAccessService access;
    private final MembershipAuditService audit;
    private final Clock clock;

    public CompanyMembershipService(CompanyRepository companies, CompanyMembershipRepository memberships,
                                     UserRepository users, UserProvisioningService provisioning,
                                     TenantAccessService access, MembershipAuditService audit, Clock clock) {
        this.companies = companies;
        this.memberships = memberships;
        this.users = users;
        this.provisioning = provisioning;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MembershipPageResponse list(UUID companyId, int page, int size) {
        access.requireCompanyRole(companyId, MembershipRole.COMPANY_ADMIN);
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.MEMBERSHIP_PAGE_INVALID,
                "Page must be nonnegative and size must be between 1 and 100");
        }
        var result = memberships.findByCompanyIdAndDeletedAtIsNull(companyId,
            PageRequest.of(page, size, Sort.by("createdAt", "id")));
        Map<UUID, User> members = users.findAllById(result.stream().map(CompanyMembership::getUserId).toList()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        var items = result.stream().map(membership -> MembershipResponse.from(membership, members.get(membership.getUserId()))).toList();
        return new MembershipPageResponse(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public MembershipResponse add(UUID companyId, AddMembershipRequest request) {
        lockCompany(companyId);
        User user = activeUser(request.userId());
        if (memberships.findByCompanyIdAndUserIdAndDeletedAtIsNull(companyId, user.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.MEMBERSHIP_ALREADY_EXISTS,
                "User already belongs to the company");
        }
        return MembershipResponse.from(createMembership(companyId, user, request.roles()), user);
    }

    public UserResponse register(RegisterUserRequest request) {
        lockCompany(request.companyId());
        User user = provisioning.create(new NewUserRequest(request.email(), request.password(), request.fullName()));
        CompanyMembership membership = createMembership(request.companyId(), user, request.roles());
        return UserResponse.from(user, membership);
    }

    public MembershipResponse replaceRoles(UUID companyId, UUID membershipId, Set<MembershipRole> roles) {
        lockCompany(companyId);
        CompanyMembership membership = find(companyId, membershipId);
        User user = users.findById(membership.getUserId()).orElseThrow();
        protectLastAdministrator(membership, user, membership.isActive(), roles);
        String before = audit.snapshot(membership);
        if (membership.replaceRoles(roles, currentTime())) {
            memberships.flush();
            audit.record(membership, access.currentUserId(), "COMPANY_MEMBER_ROLES_CHANGED", before);
        }
        return MembershipResponse.from(membership, user);
    }

    public MembershipResponse suspend(UUID companyId, UUID membershipId) {
        lockCompany(companyId);
        CompanyMembership membership = find(companyId, membershipId);
        User user = users.findById(membership.getUserId()).orElseThrow();
        protectLastAdministrator(membership, user, false, membership.getRoles());
        String before = audit.snapshot(membership);
        if (membership.suspend(currentTime())) {
            memberships.flush();
            audit.record(membership, access.currentUserId(), "COMPANY_MEMBER_SUSPENDED", before);
        }
        return MembershipResponse.from(membership, user);
    }

    public MembershipResponse activate(UUID companyId, UUID membershipId) {
        lockCompany(companyId);
        CompanyMembership membership = find(companyId, membershipId);
        User user = activeUser(membership.getUserId());
        String before = audit.snapshot(membership);
        if (membership.activate(currentTime())) {
            memberships.flush();
            audit.record(membership, access.currentUserId(), "COMPANY_MEMBER_ACTIVATED", before);
        }
        return MembershipResponse.from(membership, user);
    }

    private void lockCompany(UUID companyId) {
        access.requireActiveMembership(companyId);
        companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "Company is not active"));
        access.requireCompanyRole(companyId, MembershipRole.COMPANY_ADMIN);
    }

    private CompanyMembership find(UUID companyId, UUID membershipId) {
        return memberships.findByIdAndCompanyIdAndDeletedAtIsNull(membershipId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.MEMBERSHIP_NOT_FOUND,
                "Membership not found"));
    }

    private User activeUser(UUID userId) {
        return users.findByIdAndDeletedAtIsNull(userId).filter(User::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.ACTIVE_USER_NOT_FOUND,
                "Active user not found"));
    }

    private CompanyMembership createMembership(UUID companyId, User user, Set<MembershipRole> roles) {
        CompanyMembership membership = memberships.saveAndFlush(CompanyMembership.create(companyId, user.getId(), roles));
        audit.record(membership, access.currentUserId(), "COMPANY_MEMBER_ADDED", null);
        return membership;
    }

    private void protectLastAdministrator(CompanyMembership membership, User user, boolean remainsActive,
                                           Set<MembershipRole> remainingRoles) {
        boolean losesAdministration = !remainsActive || !remainingRoles.contains(MembershipRole.COMPANY_ADMIN);
        if (user.isActive() && membership.isActive() && membership.getRoles().contains(MembershipRole.COMPANY_ADMIN)
            && losesAdministration && memberships.countActiveAdministrators(membership.getCompanyId()) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.LAST_COMPANY_ADMIN_REQUIRED,
                "The company must retain an active administrator");
        }
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
