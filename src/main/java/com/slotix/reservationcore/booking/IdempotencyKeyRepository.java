package com.slotix.reservationcore.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByCompanyIdAndIdempotencyKey(UUID companyId, String idempotencyKey);

    @Modifying
    @Query("delete from IdempotencyKey entry where entry.companyId = :companyId and entry.idempotencyKey = :key "
        + "and entry.expiresAt <= :now")
    int deleteExpired(@Param("companyId") UUID companyId, @Param("key") String key, @Param("now") java.time.Instant now);
}
