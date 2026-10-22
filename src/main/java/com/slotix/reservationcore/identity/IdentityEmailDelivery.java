package com.slotix.reservationcore.identity;

import java.util.UUID;

public record IdentityEmailDelivery(UUID id, UUID claimToken, int attempt, String recipient,
                                    String token, String purpose, String companyName) {
    @Override public String toString() {
        return "IdentityEmailDelivery[id=" + id + ", token=[REDACTED], recipient=[REDACTED]]";
    }
}
