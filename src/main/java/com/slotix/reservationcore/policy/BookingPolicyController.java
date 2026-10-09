package com.slotix.reservationcore.policy;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/booking-policies")
public class BookingPolicyController {
    private final BookingPolicyService service;

    public BookingPolicyController(BookingPolicyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public List<BookingPolicyResponse> list(@PathVariable UUID companyId) {
        return service.list(companyId);
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<Map<String, UUID>> create(@PathVariable UUID companyId,
                                                     @Valid @RequestBody CreateBookingPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", service.create(companyId, request)));
    }

    @PutMapping("/{policyId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public BookingPolicyResponse update(@PathVariable UUID companyId, @PathVariable UUID policyId,
                                        @Valid @RequestBody UpdateBookingPolicyRequest request) {
        return service.update(companyId, policyId, request);
    }

    @PostMapping("/{policyId}/activate")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public BookingPolicyResponse activate(@PathVariable UUID companyId, @PathVariable UUID policyId) {
        return service.activate(companyId, policyId);
    }

    @PostMapping("/{policyId}/deactivate")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public BookingPolicyResponse deactivate(@PathVariable UUID companyId, @PathVariable UUID policyId) {
        return service.deactivate(companyId, policyId);
    }
}
