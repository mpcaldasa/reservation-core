package com.slotix.reservationcore.audit;

import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {
    private final JdbcTemplate jdbc;

    public AuditQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AuditPageResponse search(UUID companyId, String action, String entityType, UUID entityId,
                                    int page, int size) {
        validatePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        addFilter(where, args, "company_id", companyId);
        addFilter(where, args, "action", normalize(action));
        addFilter(where, args, "entity_type", normalize(entityType));
        addFilter(where, args, "entity_id", entityId);
        long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<AuditEntryResponse> items = jdbc.query("SELECT id, company_id, actor_user_id, action, entity_type, "
            + "entity_id, before_data::text AS before_data, after_data::text AS after_data, correlation_id, created_at "
            + "FROM audit_logs" + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            AuditQueryService::map, args.toArray());
        return new AuditPageResponse(items, page, size, total, totalPages(total, size));
    }

    private static void addFilter(StringBuilder where, List<Object> args, String column, Object value) {
        if (value != null) {
            where.append(" AND ").append(column).append(" = ?");
            args.add(value);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static AuditEntryResponse map(ResultSet row, int index) throws SQLException {
        return new AuditEntryResponse(row.getObject("id", UUID.class), row.getObject("company_id", UUID.class),
            row.getObject("actor_user_id", UUID.class), row.getString("action"), row.getString("entity_type"),
            row.getObject("entity_id", UUID.class), row.getString("before_data"), row.getString("after_data"),
            row.getString("correlation_id"), row.getTimestamp("created_at").toInstant());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.OPERATIONAL_PAGE_INVALID,
                "Page must be nonnegative and size must be between 1 and 100");
        }
    }

    private static int totalPages(long total, int size) {
        return total == 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
