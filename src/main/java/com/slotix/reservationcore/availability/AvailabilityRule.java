package com.slotix.reservationcore.availability;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="availability_rules")
public class AvailabilityRule {
 @Id @GeneratedValue private UUID id;
 @Column(name="company_id",nullable=false) private UUID companyId;
 @Column(name="resource_id",nullable=false) private UUID resourceId;
 @Column(nullable=false) private short weekday;
 @Column(name="start_local_time",nullable=false) private LocalTime startLocalTime;
 @Column(name="end_local_time",nullable=false) private LocalTime endLocalTime;
 @Column(name="effective_from") private LocalDate effectiveFrom;
 @Column(name="effective_to") private LocalDate effectiveTo;
 @Column(nullable=false) private String status;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Column(name="deleted_at") private Instant deletedAt;
 protected AvailabilityRule() {}
 public static AvailabilityRule create(UUID companyId,UUID resourceId,short weekday,LocalTime start,LocalTime end,LocalDate from,LocalDate to){
  AvailabilityRule r=new AvailabilityRule(); r.companyId=companyId;r.resourceId=resourceId;r.weekday=weekday;r.startLocalTime=start;r.endLocalTime=end;r.effectiveFrom=from;r.effectiveTo=to;r.status="ACTIVE";r.createdAt=Instant.now();r.updatedAt=r.createdAt;return r;
 }
 public UUID getId(){return id;} public UUID getCompanyId(){return companyId;} public UUID getResourceId(){return resourceId;} public short getWeekday(){return weekday;} public LocalTime getStartLocalTime(){return startLocalTime;} public LocalTime getEndLocalTime(){return endLocalTime;} public LocalDate getEffectiveFrom(){return effectiveFrom;} public LocalDate getEffectiveTo(){return effectiveTo;}
}
