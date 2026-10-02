package com.slotix.reservationcore.availability;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.resource.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ResourceBlockService {
    private final ResourceBlockRepository blocks;
    private final ResourceRepository resources;
    private final TenantAccessService access;
    private final AuditService audit;

    public ResourceBlockService(ResourceBlockRepository blocks, ResourceRepository resources,
                                TenantAccessService access, AuditService audit) {
        this.blocks = blocks;
        this.resources = resources;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public ResourceBlockResponse create(UUID companyId, UUID resourceId, CreateResourceBlockRequest request) {
        access.requireActiveMembership(companyId);
        if (!request.endAt().isAfter(request.startAt())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "The block interval is invalid");
        }
        resources.findByIdAndCompanyIdAndDeletedAtIsNull(resourceId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        ResourceBlock block = blocks.save(ResourceBlock.create(companyId, resourceId, request.startAt(), request.endAt(),
            request.reason().trim(), request.blockType()));
        audit.record(companyId, access.currentUserId(), "RESOURCE_BLOCK_CREATED", "RESOURCE_BLOCK", block.getId(), null,
            "{\"resourceId\":\"" + resourceId + "\",\"blockType\":\"" + request.blockType() + "\"}");
        return ResourceBlockResponse.from(block);
    }

    @Transactional(readOnly = true)
    public List<ResourceBlockResponse> list(UUID companyId, UUID resourceId) {
        access.requireActiveMembership(companyId);
        return blocks.findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByStartAtAsc(companyId, resourceId)
            .stream().map(ResourceBlockResponse::from).toList();
    }
}
