package com.slotix.reservationcore.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface BookingResourceRepository extends JpaRepository<BookingResource, BookingResourceId> {
    @Query("select count(r) from BookingResource r where r.id.resourceId = :resourceId " +
        "and r.status in (com.slotix.reservationcore.booking.BookingStatus.PENDING, " +
        "com.slotix.reservationcore.booking.BookingStatus.CONFIRMED, " +
        "com.slotix.reservationcore.booking.BookingStatus.CHECKED_IN) " +
        "and r.startAt < :end and r.endAt > :start")
    long occupied(@Param("resourceId") UUID resourceId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("select r from BookingResource r where r.id.resourceId = :resourceId " +
        "and r.status in (com.slotix.reservationcore.booking.BookingStatus.PENDING, " +
        "com.slotix.reservationcore.booking.BookingStatus.CONFIRMED, " +
        "com.slotix.reservationcore.booking.BookingStatus.CHECKED_IN) " +
        "and r.startAt < :end and r.endAt > :start")
    List<BookingResource> activeInRange(@Param("resourceId") UUID resourceId, @Param("start") Instant start,
                                        @Param("end") Instant end);

    Optional<BookingResource> findByIdBookingIdAndIdResourceId(UUID bookingId, UUID resourceId);
}
