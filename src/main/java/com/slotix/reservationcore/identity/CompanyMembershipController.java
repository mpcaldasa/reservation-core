package com.slotix.reservationcore.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/memberships")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class CompanyMembershipController {
    private final CompanyMembershipService service;
    private final CompanyInvitationService invitations;

    public CompanyMembershipController(CompanyMembershipService service, CompanyInvitationService invitations) {
        this.service = service;
        this.invitations = invitations;
    }

    @GetMapping
    public MembershipPageResponse list(@PathVariable UUID companyId, @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.list(companyId, page, size);
    }

    @PostMapping
    public ResponseEntity<MembershipResponse> add(@PathVariable UUID companyId, @Valid @RequestBody AddMembershipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(companyId, request));
    }

    @PostMapping("/invitations")
    public ResponseEntity<CompanyInvitationResponse> invite(@PathVariable UUID companyId,
                                                              @Valid @RequestBody CreateCompanyInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(invitations.invite(companyId, request));
    }

    @PutMapping("/{membershipId}/roles")
    public MembershipResponse roles(@PathVariable UUID companyId, @PathVariable UUID membershipId,
                                     @Valid @RequestBody UpdateMembershipRolesRequest request) {
        return service.replaceRoles(companyId, membershipId, request.roles());
    }

    @PostMapping("/{membershipId}/suspend")
    public MembershipResponse suspend(@PathVariable UUID companyId, @PathVariable UUID membershipId) {
        return service.suspend(companyId, membershipId);
    }

    @PostMapping("/{membershipId}/activate")
    public MembershipResponse activate(@PathVariable UUID companyId, @PathVariable UUID membershipId) {
        return service.activate(companyId, membershipId);
    }
}
