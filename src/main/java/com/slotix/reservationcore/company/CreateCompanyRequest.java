package com.slotix.reservationcore.company;

public record CreateCompanyRequest(
    String legalName,
    String displayName,
    String slug,
    String contactEmail
) {
}
