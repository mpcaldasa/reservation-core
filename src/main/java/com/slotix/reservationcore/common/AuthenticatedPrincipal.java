package com.slotix.reservationcore.common;

import java.util.UUID;

public record AuthenticatedPrincipal(UUID userId, UUID companyId, int credentialVersion) {
    public AuthenticatedPrincipal(UUID userId, UUID companyId) {
        this(userId, companyId, 0);
    }
}
