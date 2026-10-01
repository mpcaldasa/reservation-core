package com.slotix.reservationcore.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ResourcePolicyRepository extends JpaRepository<ResourcePolicy, ResourcePolicyId> {
    List<ResourcePolicy> findByIdResourceIdOrderByIdEffectiveFromAsc(UUID resourceId);

    @Query("select count(a) > 0 from ResourcePolicy a where a.id.resourceId = :resourceId " +
        "and a.id.effectiveFrom < :to and (a.effectiveTo is null or a.effectiveTo > :from)")
    boolean overlapsBounded(@Param("resourceId") UUID resourceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(a) > 0 from ResourcePolicy a where a.id.resourceId = :resourceId " +
        "and (a.effectiveTo is null or a.effectiveTo > :from)")
    boolean overlapsOpen(@Param("resourceId") UUID resourceId, @Param("from") Instant from);
}
