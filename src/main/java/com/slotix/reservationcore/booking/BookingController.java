package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.common.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER','CUSTOMER')")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) { this.service = service; }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                   @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(companyId(), key, request));
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingResponse get(@PathVariable UUID bookingId) { return service.get(companyId(), bookingId); }

    @GetMapping("/bookings")
    public List<BookingResponse> list(@RequestParam Instant from, @RequestParam Instant to) {
        return service.calendar(companyId(), from, to);
    }

    @GetMapping("/calendar")
    public List<BookingResponse> calendar(@RequestParam Instant from, @RequestParam Instant to) {
        return service.calendar(companyId(), from, to);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable UUID bookingId, @RequestBody(required = false) CancelBookingRequest request) {
        return service.cancel(companyId(), bookingId, request == null ? null : request.reason());
    }

    private UUID companyId() {
        return ((AuthenticatedPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).companyId();
    }
}
