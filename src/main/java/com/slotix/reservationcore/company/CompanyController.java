package com.slotix.reservationcore.company;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;

    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @GetMapping
    public List<CompanyResponse> listCompanies() {
        return companyRepository.findAll()
            .stream()
            .map(CompanyResponse::from)
            .toList();
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        Company company = Company.create(
            request.legalName(),
            request.displayName(),
            request.slug(),
            request.contactEmail()
        );
        Company saved = companyRepository.save(company);
        return ResponseEntity.ok(CompanyResponse.from(saved));
    }

    @PostMapping("/{companyId}/activate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<CompanyResponse> activateCompany(@PathVariable UUID companyId) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        company.activate();
        return ResponseEntity.ok(CompanyResponse.from(companyRepository.save(company)));
    }
}
