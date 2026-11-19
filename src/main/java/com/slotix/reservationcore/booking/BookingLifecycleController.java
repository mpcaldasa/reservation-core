package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.common.AuthenticatedPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER')")
public class BookingLifecycleController {
    private final BookingLifecycleService service;
    private final BookingReschedulingService reschedulingService;

    public BookingLifecycleController(BookingLifecycleService service, BookingReschedulingService reschedulingService) {
        this.service = service;
        this.reschedulingService = reschedulingService;
    }

    @PostMapping("/check-in")
    public BookingResponse checkIn(@PathVariable UUID bookingId) {
        return service.checkIn(companyId(), bookingId);
    }

    @PostMapping("/complete")
    public BookingResponse complete(@PathVariable UUID bookingId) {
        return service.complete(companyId(), bookingId);
    }

    @PostMapping("/no-show")
    public BookingResponse noShow(@PathVariable UUID bookingId) {
        return service.markNoShow(companyId(), bookingId);
    }

    @PutMapping("/reschedule")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER','CUSTOMER')")
    public BookingResponse reschedule(@PathVariable UUID bookingId, @Valid @RequestBody RescheduleBookingRequest request) {
        return reschedulingService.reschedule(companyId(), bookingId, request);
    }

    private UUID companyId() {
        return ((AuthenticatedPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).companyId();
    }
}
