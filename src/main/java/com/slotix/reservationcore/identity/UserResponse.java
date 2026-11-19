package com.slotix.reservationcore.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String fullName,
    String status,
    Instant createdAt,
    UUID companyId,
    List<String> roles
) {
    public static UserResponse from(User user, CompanyMembership membership) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getStatus(),
            user.getCreatedAt(),
            membership.getCompanyId(),
            membership.getRoles().stream().map(Enum::name).sorted().toList()
        );
    }
}
