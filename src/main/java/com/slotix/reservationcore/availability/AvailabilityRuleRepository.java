package com.slotix.reservationcore.availability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule,UUID>{ List<AvailabilityRule> findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByWeekdayAscStartLocalTimeAsc(UUID companyId,UUID resourceId); }
