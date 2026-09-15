package com.slotix.reservationcore.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, UUID> {

    Optional<CompanyMembership> findByCompanyIdAndUserIdAndDeletedAtIsNull(
        UUID companyId,
        UUID userId
    );
}
