package com.slotix.reservationcore.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CompanyInvitationResponse(UUID id, UUID companyId, String email, List<String> roles,
                                        Instant expiresAt, String status) {
    public static CompanyInvitationResponse from(CompanyInvitation invitation) {
        return new CompanyInvitationResponse(invitation.getId(), invitation.getCompanyId(), invitation.getInvitedEmail(),
            invitation.getRoles().stream().map(Enum::name).sorted().toList(),
            invitation.getExpiresAt(), invitation.getStatus());
    }
}
