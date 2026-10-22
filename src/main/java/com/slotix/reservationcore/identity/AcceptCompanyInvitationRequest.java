package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptCompanyInvitationRequest(@NotBlank @Size(max = 128) String token,
                                              @NotBlank @Size(min = 8, max = 72) String password,
                                              @NotBlank @Size(max = 120) String fullName) {
    @Override
    public String toString() {
        return "AcceptCompanyInvitationRequest[token=[REDACTED], password=[REDACTED], fullName=" + fullName + "]";
    }
}
