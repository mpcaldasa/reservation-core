package com.slotix.reservationcore.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id and b.companyId = :companyId")
    Optional<Booking> lockByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select b from Booking b where b.status = com.slotix.reservationcore.booking.BookingStatus.PENDING "
        + "and b.createdAt <= :cutoff order by b.createdAt, b.id")
    List<Booking> lockExpiredPending(@Param("cutoff") Instant cutoff, Pageable pageable);
    @Query("select b from Booking b where b.companyId = :companyId and b.startAt < :to and b.endAt > :from order by b.startAt")
    List<Booking> inCalendar(@Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);
}
