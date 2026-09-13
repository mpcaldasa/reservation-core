package com.slotix.reservationcore.identity;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    UUID companyId,
    String email,
    String fullName,
    String role,
    String status,
    Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getCompanyId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt()
        );
    }
}
