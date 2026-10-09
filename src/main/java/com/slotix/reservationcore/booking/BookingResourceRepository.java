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

    @Query("select r from BookingResource r where r.id.resourceId = :resourceId and r.id.bookingId <> :excludedBookingId "
        + "and r.status in (com.slotix.reservationcore.booking.BookingStatus.PENDING, "
        + "com.slotix.reservationcore.booking.BookingStatus.CONFIRMED, "
        + "com.slotix.reservationcore.booking.BookingStatus.CHECKED_IN) "
        + "and r.startAt < :end and r.endAt > :start")
    List<BookingResource> activeInRangeExcludingBooking(@Param("resourceId") UUID resourceId,
        @Param("excludedBookingId") UUID excludedBookingId, @Param("start") Instant start, @Param("end") Instant end);

    Optional<BookingResource> findByIdBookingIdAndIdResourceId(UUID bookingId, UUID resourceId);

    @Query("select count(r) from BookingResource r where r.id.resourceId = :resourceId "
        + "and r.status in (com.slotix.reservationcore.booking.BookingStatus.PENDING, "
        + "com.slotix.reservationcore.booking.BookingStatus.CONFIRMED, "
        + "com.slotix.reservationcore.booking.BookingStatus.CHECKED_IN) and r.endAt > :now")
    long countActiveReservationsAfter(@Param("resourceId") UUID resourceId, @Param("now") Instant now);

    @Query("select count(r) > 0 from BookingResource r where r.id.resourceId = :resourceId "
        + "and r.status in (com.slotix.reservationcore.booking.BookingStatus.PENDING, "
        + "com.slotix.reservationcore.booking.BookingStatus.CONFIRMED, "
        + "com.slotix.reservationcore.booking.BookingStatus.CHECKED_IN) "
        + "and r.startAt >= :effectiveFrom and r.startAt < :effectiveTo")
    boolean hasActiveReservationInBoundedAssignment(@Param("resourceId") UUID resourceId,
        @Param("effectiveFrom") Instant effectiveFrom, @Param("effectiveTo") Instant effectiveTo);

    @Query("select count(r) > 0 from BookingResource r where r.id.resourceId = :resourceId "
        + "and r.status in (com.slotix.reservationcore.booking.BookingStatus.PENDING, "
        + "com.slotix.reservationcore.booking.BookingStatus.CONFIRMED, "
        + "com.slotix.reservationcore.booking.BookingStatus.CHECKED_IN) "
        + "and r.startAt >= :effectiveFrom")
    boolean hasActiveReservationInOpenAssignment(@Param("resourceId") UUID resourceId,
        @Param("effectiveFrom") Instant effectiveFrom);

    @Query("select count(r) > 0 from BookingResource r where r.id.resourceId = :resourceId "
        + "and r.status in (com.slotix.reservationcore.booking.BookingStatus.PENDING, "
        + "com.slotix.reservationcore.booking.BookingStatus.CONFIRMED, "
        + "com.slotix.reservationcore.booking.BookingStatus.CHECKED_IN) "
        + "and r.startAt >= :effectiveFrom and r.startAt < :oldEffectiveTo and r.endAt > :newEffectiveTo")
    boolean hasActiveReservationOutsideShortenedBoundedAssignment(@Param("resourceId") UUID resourceId,
        @Param("effectiveFrom") Instant effectiveFrom, @Param("oldEffectiveTo") Instant oldEffectiveTo,
        @Param("newEffectiveTo") Instant newEffectiveTo);

    @Query("select count(r) > 0 from BookingResource r where r.id.resourceId = :resourceId "
        + "and r.status in (com.slotix.reservationcore.booking.BookingStatus.PENDING, "
        + "com.slotix.reservationcore.booking.BookingStatus.CONFIRMED, "
        + "com.slotix.reservationcore.booking.BookingStatus.CHECKED_IN) "
        + "and r.startAt >= :effectiveFrom and r.endAt > :newEffectiveTo")
    boolean hasActiveReservationOutsideShortenedOpenAssignment(@Param("resourceId") UUID resourceId,
        @Param("effectiveFrom") Instant effectiveFrom, @Param("newEffectiveTo") Instant newEffectiveTo);
}
