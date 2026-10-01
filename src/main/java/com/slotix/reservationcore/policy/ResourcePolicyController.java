package com.slotix.reservationcore.policy;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/resources/{resourceId}/policies")
public class ResourcePolicyController {
    private final ResourcePolicyService service;

    public ResourcePolicyController(ResourcePolicyService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ResourcePolicyResponse> assign(@PathVariable UUID companyId, @PathVariable UUID resourceId,
                                                          @Valid @RequestBody AssignResourcePolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(companyId, resourceId, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER','CUSTOMER')")
    public List<ResourcePolicyResponse> list(@PathVariable UUID companyId, @PathVariable UUID resourceId) {
        return service.list(companyId, resourceId);
    }
}
