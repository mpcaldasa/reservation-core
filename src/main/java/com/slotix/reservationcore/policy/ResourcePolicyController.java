package com.slotix.reservationcore.policy;

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

import java.time.Instant;
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

    @PutMapping("/{effectiveFrom}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourcePolicyResponse close(@PathVariable UUID companyId, @PathVariable UUID resourceId,
                                        @PathVariable Instant effectiveFrom,
                                        @Valid @RequestBody CloseResourcePolicyAssignmentRequest request) {
        return service.close(companyId, resourceId, effectiveFrom, request);
    }

    @PutMapping("/{effectiveFrom}/policy")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourcePolicyResponse replacePolicy(@PathVariable UUID companyId, @PathVariable UUID resourceId,
                                                @PathVariable Instant effectiveFrom,
                                                @Valid @RequestBody ReplaceResourcePolicyRequest request) {
        return service.replacePolicy(companyId, resourceId, effectiveFrom, request);
    }

    @DeleteMapping("/{effectiveFrom}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<Void> removeFuture(@PathVariable UUID companyId, @PathVariable UUID resourceId,
                                             @PathVariable Instant effectiveFrom) {
        service.removeFuture(companyId, resourceId, effectiveFrom);
        return ResponseEntity.noContent().build();
    }
}
