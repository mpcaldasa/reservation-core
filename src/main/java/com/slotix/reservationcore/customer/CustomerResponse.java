package com.slotix.reservationcore.customer;

import java.util.UUID;

public record CustomerResponse(UUID userId, String fullName, String email, String phone,
                               String notes, boolean active) {}
