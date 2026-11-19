package com.slotix.reservationcore.availability;
import java.time.*; import java.util.*;
public record AvailabilityRuleResponse(UUID id,short weekday,LocalTime startLocalTime,LocalTime endLocalTime,LocalDate effectiveFrom,LocalDate effectiveTo){static AvailabilityRuleResponse from(AvailabilityRule r){return new AvailabilityRuleResponse(r.getId(),r.getWeekday(),r.getStartLocalTime(),r.getEndLocalTime(),r.getEffectiveFrom(),r.getEffectiveTo());}}
