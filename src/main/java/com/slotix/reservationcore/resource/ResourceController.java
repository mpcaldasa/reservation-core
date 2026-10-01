package com.slotix.reservationcore.resource;

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
@RequestMapping("/api/v1/companies/{companyId}/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'BOOKING_MANAGER', 'CUSTOMER')")
    public List<ResourceResponse> list(@PathVariable UUID companyId) {
        return resourceService.list(companyId);
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ResourceResponse> create(
        @PathVariable UUID companyId,
        @Valid @RequestBody CreateResourceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(resourceService.create(companyId, request));
    }

    @PostMapping("/{resourceId}/activate")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourceResponse activate(@PathVariable UUID companyId, @PathVariable UUID resourceId) {
        return resourceService.activate(companyId, resourceId);
    }
}
