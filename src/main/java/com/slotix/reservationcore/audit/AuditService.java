package com.slotix.reservationcore.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID companyId, UUID actorId, String action, String entityType, UUID entityId,
                       String beforeJson, String afterJson) {
        jdbc.update("insert into audit_logs(company_id,actor_user_id,action,entity_type,entity_id,before_data,after_data) " +
                "values (?,?,?,?,?,cast(? as jsonb),cast(? as jsonb))",
            companyId, actorId, action, entityType, entityId, beforeJson, afterJson);
    }
}
