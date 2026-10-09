package com.slotix.reservationcore.availability;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/resources/{resourceId}/availability")
public class AvailabilityController {
    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER','CUSTOMER')")
    public List<AvailabilitySlot> slots(@PathVariable UUID companyId, @PathVariable UUID resourceId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam int durationMinutes) {
        return service.slots(companyId, resourceId, date, durationMinutes);
    }
}
