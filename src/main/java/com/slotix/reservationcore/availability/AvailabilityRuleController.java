package com.slotix.reservationcore.availability;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/resources/{resourceId}/availability-rules")
public class AvailabilityRuleController {
    private final AvailabilityRuleService service;

    public AvailabilityRuleController(AvailabilityRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER','CUSTOMER')")
    public List<AvailabilityRuleResponse> list(@PathVariable UUID companyId, @PathVariable UUID resourceId) {
        return service.list(companyId, resourceId);
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<AvailabilityRuleResponse> create(@PathVariable UUID companyId, @PathVariable UUID resourceId,
                                                  @Valid @RequestBody CreateAvailabilityRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(companyId, resourceId, request));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public AvailabilityRuleResponse update(@PathVariable UUID companyId, @PathVariable UUID resourceId,
                                          @PathVariable UUID ruleId,
                                          @Valid @RequestBody UpdateAvailabilityRuleRequest request) {
        return service.update(companyId, resourceId, ruleId, request);
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID companyId, @PathVariable UUID resourceId,
                                       @PathVariable UUID ruleId) {
        service.delete(companyId, resourceId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
