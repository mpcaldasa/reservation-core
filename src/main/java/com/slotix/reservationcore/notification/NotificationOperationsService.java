package com.slotix.reservationcore.notification;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class NotificationOperationsService {
    private static final List<String> STATUSES = List.of("PENDING", "SENDING", "SENT", "FAILED");
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final TenantAccessService access;

    public NotificationOperationsService(JdbcTemplate jdbc, AuditService audit, TenantAccessService access) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public NotificationDeliveryPageResponse list(String status, int page, int size) {
        validatePage(page, size);
        String normalized = status == null ? "FAILED" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.NOTIFICATION_STATUS_INVALID,
                "Status must be one of PENDING, SENDING, SENT, or FAILED");
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM notification_deliveries WHERE status = ?", Long.class,
            normalized);
        List<NotificationDeliveryResponse> items = jdbc.query("SELECT id, company_id, booking_id, template, status, "
            + "attempts, error_message, next_attempt_at, created_at, sent_at FROM notification_deliveries "
            + "WHERE status = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            NotificationOperationsService::map, normalized, size, (long) page * size);
        return new NotificationDeliveryPageResponse(items, page, size, total, totalPages(total, size));
    }

    public NotificationDeliveryResponse replay(UUID deliveryId) {
        NotificationDeliveryResponse delivery = jdbc.query("SELECT id, company_id, booking_id, template, status, "
            + "attempts, error_message, next_attempt_at, created_at, sent_at FROM notification_deliveries WHERE id = ? "
            + "FOR UPDATE", NotificationOperationsService::map, deliveryId).stream().findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOTIFICATION_DELIVERY_NOT_FOUND,
                "Notification delivery was not found"));
        if (!"FAILED".equals(delivery.status())) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.NOTIFICATION_DELIVERY_NOT_REPLAYABLE,
                "Only failed notification deliveries can be replayed");
        }
        jdbc.update("UPDATE notification_deliveries SET status = 'PENDING', attempts = 0, next_attempt_at = now(), "
            + "error_message = NULL WHERE id = ? AND status = 'FAILED'", deliveryId);
        audit.record(delivery.companyId(), access.currentUserId(), "NOTIFICATION_DELIVERY_REPLAYED",
            "NOTIFICATION_DELIVERY", deliveryId, "{\"status\":\"FAILED\"}", "{\"status\":\"PENDING\"}");
        return jdbc.query("SELECT id, company_id, booking_id, template, status, attempts, error_message, "
            + "next_attempt_at, created_at, sent_at FROM notification_deliveries WHERE id = ?",
            NotificationOperationsService::map, deliveryId).get(0);
    }

    private static NotificationDeliveryResponse map(ResultSet row, int index) throws SQLException {
        return new NotificationDeliveryResponse(row.getObject("id", UUID.class),
            row.getObject("company_id", UUID.class), row.getObject("booking_id", UUID.class),
            row.getString("template"), row.getString("status"), row.getInt("attempts"),
            row.getString("error_message"), row.getTimestamp("next_attempt_at").toInstant(),
            row.getTimestamp("created_at").toInstant(), row.getTimestamp("sent_at") == null ? null
                : row.getTimestamp("sent_at").toInstant());
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
