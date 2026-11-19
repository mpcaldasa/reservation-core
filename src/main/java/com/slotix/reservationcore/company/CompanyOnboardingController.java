package com.slotix.reservationcore.company;

import com.slotix.reservationcore.identity.InitialAdministratorRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class CompanyOnboardingController {
    private final CompanyOnboardingService service;

    public CompanyOnboardingController(CompanyOnboardingService service) {
        this.service = service;
    }

    @PostMapping("/onboarding")
    public ResponseEntity<CompanyOnboardingResponse> onboard(@Valid @RequestBody CompanyOnboardingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.onboard(request));
    }

    @PostMapping("/{companyId}/initial-administrator")
    public CompanyOnboardingResponse initialize(@PathVariable UUID companyId, @Valid @RequestBody InitialAdministratorRequest request) {
        return service.assignInitialAdministrator(companyId, request);
    }
}
