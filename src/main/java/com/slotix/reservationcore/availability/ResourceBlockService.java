package com.slotix.reservationcore.availability;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.resource.ResourceAccessService;
import com.slotix.reservationcore.resource.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ResourceBlockService {
    private final ResourceBlockRepository blocks;
    private final CompanyRepository companies;
    private final ResourceRepository resources;
    private final ResourceAccessService resourceAccess;
    private final TenantAccessService access;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ResourceBlockService(ResourceBlockRepository blocks, CompanyRepository companies,
                                ResourceRepository resources, ResourceAccessService resourceAccess,
                                TenantAccessService access, AuditService audit, ObjectMapper objectMapper,
                                Clock clock) {
        this.blocks = blocks;
        this.companies = companies;
        this.resources = resources;
        this.resourceAccess = resourceAccess;
        this.access = access;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourceBlockResponse create(UUID companyId, UUID resourceId, CreateResourceBlockRequest request) {
        validate(request.startAt(), request.endAt(), request.reason());
        lockCompanyAndResource(companyId, resourceId);
        Instant startAt = normalize(request.startAt());
        Instant endAt = normalize(request.endAt());
        ResourceBlock block = blocks.save(ResourceBlock.create(companyId, resourceId, startAt, endAt,
            request.reason().trim(), request.blockType()));
        audit.record(companyId, access.currentUserId(), "RESOURCE_BLOCK_CREATED", "RESOURCE_BLOCK", block.getId(),
            null, snapshot(block));
        return ResourceBlockResponse.from(block);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourceBlockResponse update(UUID companyId, UUID resourceId, UUID blockId,
                                        UpdateResourceBlockRequest request) {
        validate(request.startAt(), request.endAt(), request.reason());
        lockCompanyAndResource(companyId, resourceId);
        ResourceBlock block = lockBlock(companyId, resourceId, blockId);
        if (block.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_BLOCK_NOT_FOUND,
                "Resource block not found");
        }
        String before = snapshot(block);
        if (block.update(normalize(request.startAt()), normalize(request.endAt()), request.reason().trim(),
            request.blockType(), now())) {
            audit.record(companyId, access.currentUserId(), "RESOURCE_BLOCK_UPDATED", "RESOURCE_BLOCK", blockId,
                before, snapshot(block));
        }
        return ResourceBlockResponse.from(block);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public void delete(UUID companyId, UUID resourceId, UUID blockId) {
        lockCompanyAndResource(companyId, resourceId);
        ResourceBlock block = lockBlock(companyId, resourceId, blockId);
        if (block.softDelete(now())) {
            audit.record(companyId, access.currentUserId(), "RESOURCE_BLOCK_DELETED", "RESOURCE_BLOCK", blockId,
                snapshot(block), null);
        }
    }

    @Transactional(readOnly = true)
    public List<ResourceBlockResponse> list(UUID companyId, UUID resourceId) {
        resourceAccess.requireVisibleResource(companyId, resourceId);
        return blocks.findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByStartAtAsc(companyId, resourceId)
            .stream().map(ResourceBlockResponse::from).toList();
    }

    private void lockCompanyAndResource(UUID companyId, UUID resourceId) {
        access.requireActiveMembership(companyId);
        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "Company is not active"));
        access.requireCompanyRole(company.getId(), MembershipRole.COMPANY_ADMIN);
        resources.lockByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "Resource not found"));
    }

    private ResourceBlock lockBlock(UUID companyId, UUID resourceId, UUID blockId) {
        return blocks.lockByIdAndCompanyAndResource(blockId, companyId, resourceId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_BLOCK_NOT_FOUND,
                "Resource block not found"));
    }

    private void validate(Instant startAt, Instant endAt, String reason) {
        if (startAt == null || endAt == null || !normalize(endAt).isAfter(normalize(startAt))
            || reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.RESOURCE_BLOCK_INTERVAL_INVALID,
                "The resource block requires a start, an end after the start, and a reason");
        }
    }

    private Instant normalize(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private Instant now() {
        return normalize(clock.instant());
    }

    private String snapshot(ResourceBlock block) {
        try {
            return objectMapper.writeValueAsString(new BlockSnapshot(block.getStartAt(), block.getEndAt(),
                block.getReason(), block.getBlockType()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Resource block audit snapshot could not be serialized", exception);
        }
    }

    private record BlockSnapshot(Instant startAt, Instant endAt, String reason, ResourceBlockType blockType) {
    }
}
