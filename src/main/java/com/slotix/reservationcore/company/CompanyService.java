package com.slotix.reservationcore.company;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class CompanyService {
    private final CompanyRepository companies;
    private final AuditService audit;
    private final TenantAccessService access;

    public CompanyService(CompanyRepository companies, AuditService audit, TenantAccessService access) {
        this.companies = companies;
        this.audit = audit;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list() {
        return companies.findAll().stream().map(CompanyResponse::from).toList();
    }

    public CompanyResponse create(CreateCompanyRequest request) {
        if (companies.findBySlug(request.slug()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.COMPANY_SLUG_ALREADY_EXISTS,
                "Company slug already exists");
        }
        Company company = companies.saveAndFlush(Company.create(request.legalName().trim(), request.displayName().trim(),
            request.slug(), request.contactEmail().trim()));
        audit.record(company.getId(), access.currentUserId(), "COMPANY_CREATED", "COMPANY", company.getId(), null,
            "{\"status\":\"PENDING\"}");
        return CompanyResponse.from(company);
    }

    public CompanyResponse activate(UUID companyId) {
        Company company = companies.lockById(companyId).filter(candidate -> candidate.getDeletedAt() == null)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COMPANY_NOT_FOUND,
                "Company not found"));
        if (!company.isActive()) {
            String before = "{\"status\":\"%s\"}".formatted(company.getStatus());
            company.activate();
            audit.record(companyId, access.currentUserId(), "COMPANY_ACTIVATED", "COMPANY", companyId, before,
                "{\"status\":\"ACTIVE\"}");
        }
        return CompanyResponse.from(company);
    }
}
