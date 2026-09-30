package com.slotix.reservationcore.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    List<Resource> findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(UUID companyId);

    Optional<Resource> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);
}
