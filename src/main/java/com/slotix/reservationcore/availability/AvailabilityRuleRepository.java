package com.slotix.reservationcore.availability;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {
    List<AvailabilityRule> findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByWeekdayAscStartLocalTimeAsc(
        UUID companyId, UUID resourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from AvailabilityRule rule where rule.id = :ruleId and rule.companyId = :companyId "
        + "and rule.resourceId = :resourceId")
    Optional<AvailabilityRule> lockByIdAndCompanyAndResource(@Param("ruleId") UUID ruleId,
        @Param("companyId") UUID companyId, @Param("resourceId") UUID resourceId);
}
