package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.audit.AuditService;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MembershipAuditService {
    private final AuditService audit;

    public MembershipAuditService(AuditService audit) {
        this.audit = audit;
    }

    public String snapshot(CompanyMembership membership) {
        String roles = membership.getRoles().stream().sorted().map(role -> "\"" + role.name() + "\"")
            .collect(Collectors.joining(",", "[", "]"));
        return "{\"status\":\"%s\",\"roles\":%s}".formatted(membership.getStatus(), roles);
    }

    public void record(CompanyMembership membership, UUID actor, String action, String before) {
        audit.record(membership.getCompanyId(), actor, action, "COMPANY_MEMBERSHIP", membership.getId(),
            before, snapshot(membership));
    }
}
