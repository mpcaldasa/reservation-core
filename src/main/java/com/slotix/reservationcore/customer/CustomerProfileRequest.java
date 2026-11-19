package com.slotix.reservationcore.customer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerProfileRequest(
    @NotNull @Size(max = 40) @Pattern(regexp = "[0-9+() .-]*") String phone,
    @NotNull @Size(max = 2000) String notes) {}
