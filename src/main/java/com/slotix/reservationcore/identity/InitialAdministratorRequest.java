package com.slotix.reservationcore.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

import java.util.UUID;

public record InitialAdministratorRequest(UUID existingUserId, @Valid NewUserRequest newUser) {
    @AssertTrue(message = "Specify either existingUserId or newUser")
    public boolean isSingleAdministratorSpecified() {
        return (existingUserId != null) != (newUser != null);
    }
}
