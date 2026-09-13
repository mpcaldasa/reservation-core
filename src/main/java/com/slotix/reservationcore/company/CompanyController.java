package com.slotix.reservationcore.company;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;

    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @GetMapping
    public List<Company> listCompanies() {
        return companyRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Company> createCompany(@RequestBody CreateCompanyRequest request) {
        Company company = Company.create(
            request.legalName(),
            request.displayName(),
            request.slug(),
            request.contactEmail()
        );
        Company saved = companyRepository.save(company);
        return ResponseEntity.ok(saved);
    }
}
