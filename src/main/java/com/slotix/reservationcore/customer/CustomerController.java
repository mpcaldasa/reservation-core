package com.slotix.reservationcore.customer;

import com.slotix.reservationcore.booking.BookingPageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/customers")
public class CustomerController {
    private final CustomerDirectoryService service;
    public CustomerController(CustomerDirectoryService service) { this.service = service; }

    @GetMapping
    public CustomerPageResponse search(@PathVariable UUID companyId, @RequestParam(defaultValue = "") String query,
        @RequestParam(defaultValue = "false") boolean activeOnly, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return service.search(companyId, query, activeOnly, page, size);
    }

    @GetMapping("/{userId}")
    public CustomerResponse get(@PathVariable UUID companyId, @PathVariable UUID userId) {
        return service.get(companyId, userId);
    }

    @PutMapping("/{userId}")
    public CustomerResponse update(@PathVariable UUID companyId, @PathVariable UUID userId,
                                   @Valid @RequestBody CustomerProfileRequest request) {
        return service.update(companyId, userId, request);
    }

    @GetMapping("/{userId}/bookings")
    public BookingPageResponse history(@PathVariable UUID companyId, @PathVariable UUID userId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.history(companyId, userId, page, size);
    }
}
