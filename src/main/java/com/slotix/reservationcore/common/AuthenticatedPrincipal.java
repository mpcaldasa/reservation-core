package com.slotix.reservationcore.common;

import java.util.UUID;

public record AuthenticatedPrincipal(UUID userId, UUID companyId) {
}
