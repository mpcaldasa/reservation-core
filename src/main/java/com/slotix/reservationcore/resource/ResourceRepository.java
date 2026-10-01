package com.slotix.reservationcore.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    List<Resource> findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(UUID companyId);

    Optional<Resource> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id and r.companyId = :companyId and r.deletedAt is null")
    Optional<Resource> lockByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
