package com.slotix.reservationcore.company;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
    UUID id,
    String legalName,
    String displayName,
    String slug,
    String status,
    String timezone,
    String currencyCode,
    String contactEmail,
    Instant createdAt
) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
            company.getId(),
            company.getLegalName(),
            company.getDisplayName(),
            company.getSlug(),
            company.getStatus(),
            company.getTimezone(),
            company.getCurrencyCode(),
            company.getContactEmail(),
            company.getCreatedAt()
        );
    }
}
