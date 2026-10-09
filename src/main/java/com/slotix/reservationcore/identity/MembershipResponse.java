package com.slotix.reservationcore.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MembershipResponse(UUID id, UUID companyId, UUID userId, String email, String fullName,
                                  String status, List<MembershipRole> roles, Instant joinedAt,
                                  Instant createdAt, Instant updatedAt) {
    public static MembershipResponse from(CompanyMembership membership, User user) {
        return new MembershipResponse(membership.getId(), membership.getCompanyId(), membership.getUserId(),
            user.getEmail(), user.getFullName(), membership.getStatus(), membership.getRoles().stream().sorted().toList(),
            membership.getJoinedAt(), membership.getCreatedAt(), membership.getUpdatedAt());
    }
}
