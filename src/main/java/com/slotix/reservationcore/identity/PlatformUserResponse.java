package com.slotix.reservationcore.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformUserResponse(UUID id, String email, String fullName, String status,
                                   List<String> platformRoles, Instant createdAt, Instant updatedAt) {}
