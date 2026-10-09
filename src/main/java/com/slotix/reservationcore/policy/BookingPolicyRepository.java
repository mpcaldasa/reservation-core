package com.slotix.reservationcore.policy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingPolicyRepository extends JpaRepository<BookingPolicy, UUID> {
    Optional<BookingPolicy> findByIdAndCompanyIdAndStatusAndDeletedAtIsNull(UUID id, UUID companyId, String status);

    Optional<BookingPolicy> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

    List<BookingPolicy> findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from BookingPolicy policy where policy.id = :id and policy.companyId = :companyId "
        + "and policy.deletedAt is null")
    Optional<BookingPolicy> lockByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
