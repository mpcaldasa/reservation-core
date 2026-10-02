package com.slotix.reservationcore.resource;

import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final CompanyRepository companyRepository;
    private final TenantAccessService tenantAccessService;
    private final AuditService audit;

    public ResourceService(
        ResourceRepository resourceRepository,
        CompanyRepository companyRepository,
        TenantAccessService tenantAccessService,
        AuditService audit
    ) {
        this.resourceRepository = resourceRepository;
        this.companyRepository = companyRepository;
        this.tenantAccessService = tenantAccessService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> list(UUID companyId) {
        tenantAccessService.requireActiveMembership(companyId);
        return resourceRepository.findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(companyId)
            .stream()
            .map(ResourceResponse::from)
            .toList();
    }

    @Transactional
    public ResourceResponse create(UUID companyId, CreateResourceRequest request) {
        tenantAccessService.requireActiveMembership(companyId);

        Company company = companyRepository.findById(companyId)
            .filter(Company::isActive)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "The company must be active before resources can be created"
            ));

        Resource resource = Resource.create(
            company.getId(),
            request.name().trim(),
            request.description(),
            request.resourceType(),
            request.capacity(),
            request.visibility()
        );

        Resource saved = resourceRepository.save(resource);
        audit.record(companyId, tenantAccessService.currentUserId(), "RESOURCE_CREATED", "RESOURCE", saved.getId(), null,
            "{\"status\":\"DRAFT\",\"resourceType\":\"" + saved.getResourceType() + "\"}");
        return ResourceResponse.from(saved);
    }

    @Transactional
    public ResourceResponse activate(UUID companyId, UUID resourceId) {
        tenantAccessService.requireActiveMembership(companyId);
        companyRepository.findById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Company is not active"));
        Resource resource = resourceRepository.findByIdAndCompanyIdAndDeletedAtIsNull(resourceId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        String previousStatus = resource.getStatus().name();
        resource.activate();
        audit.record(companyId, tenantAccessService.currentUserId(), "RESOURCE_ACTIVATED", "RESOURCE", resourceId,
            "{\"status\":\"" + previousStatus + "\"}", "{\"status\":\"ACTIVE\"}");
        return ResourceResponse.from(resource);
    }
}
