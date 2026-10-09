package com.slotix.reservationcore.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
public class CompanyInvitationAcceptanceController {
    private final CompanyInvitationService invitations;

    public CompanyInvitationAcceptanceController(CompanyInvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping("/accept")
    public ResponseEntity<UserResponse> accept(@Valid @RequestBody AcceptCompanyInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitations.accept(request));
    }
}
