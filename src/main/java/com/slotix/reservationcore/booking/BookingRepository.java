package com.slotix.reservationcore.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdAndCompanyId(UUID id, UUID companyId);
    @Query("select b from Booking b where b.companyId = :companyId and b.startAt < :to and b.endAt > :from order by b.startAt")
    List<Booking> inCalendar(@Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);
}
