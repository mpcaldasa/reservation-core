package com.slotix.reservationcore.notification;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reminders")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class ReminderController {
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;
    private final AuditService audit;
    private final boolean emailEnabled;

    public ReminderController(JdbcTemplate jdbc, TenantAccessService access, AuditService audit,
                              @Value("${app.notification.email.enabled:false}") boolean emailEnabled) {
        this.jdbc = jdbc; this.access = access; this.audit = audit; this.emailEnabled = emailEnabled;
    }

    @GetMapping
    public ReminderSettings get(@PathVariable UUID companyId) {
        access.requireActiveMembership(companyId);
        return jdbc.query("SELECT enabled,minutes_before FROM company_reminder_settings WHERE company_id=?",
            (row, index) -> new ReminderSettings(row.getBoolean(1), row.getInt(2)), companyId)
            .stream().findFirst().orElse(new ReminderSettings(false, 60));
    }

    public record ReminderDelivery(UUID id, UUID bookingId, long bookingNumber, String customerName,
                                   Instant startAt, String status, Instant sentAt) {}
    public record ReminderDeliveryPage(List<ReminderDelivery> items, int page, int size, long totalElements, int totalPages) {}

    @GetMapping("/history")
    public ReminderDeliveryPage history(@PathVariable UUID companyId,
                                       @RequestParam(defaultValue="0") int page,
                                       @RequestParam(defaultValue="20") int size) {
        access.requireActiveMembership(companyId);
        if(page<0 || size<1 || size>50) throw new ApiException(HttpStatus.BAD_REQUEST,ApiErrorCode.VALIDATION_FAILED,
            "Page must be non-negative and size must be between 1 and 50");
        long total=jdbc.queryForObject("SELECT count(*) FROM notification_deliveries WHERE company_id=? AND template='BOOKING_REMINDER'",Long.class,companyId);
        var items=jdbc.query("""
            SELECT d.id,b.id booking_id,b.booking_number,u.full_name,coalesce((o.payload->>'startAt')::timestamptz,b.start_at) start_at,d.status,d.sent_at
            FROM notification_deliveries d JOIN outbox_events o ON o.id=d.outbox_event_id JOIN bookings b ON b.id=d.booking_id AND b.company_id=d.company_id
            JOIN users u ON u.id=b.customer_user_id
            WHERE d.company_id=? AND d.template='BOOKING_REMINDER'
            ORDER BY d.created_at DESC,d.id DESC LIMIT ? OFFSET ?
            """,(row,index)->new ReminderDelivery(row.getObject("id",UUID.class),row.getObject("booking_id",UUID.class),
                row.getLong("booking_number"),row.getString("full_name"),row.getTimestamp("start_at").toInstant(),row.getString("status"),
                row.getTimestamp("sent_at")==null?null:row.getTimestamp("sent_at").toInstant()),companyId,size,(long)page*size);
        return new ReminderDeliveryPage(items,page,size,total,(int)((total+size-1)/size));
    }

    @PutMapping
    @Transactional
    public ReminderSettings update(@PathVariable UUID companyId, @Valid @RequestBody ReminderSettings settings) {
        access.requireActiveMembership(companyId);
        if (settings.enabled() && !emailEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.EMAIL_DELIVERY_UNAVAILABLE,
                "Email delivery must be configured before reminders can be enabled");
        }
        jdbc.update("""
            INSERT INTO company_reminder_settings(company_id,enabled,minutes_before) VALUES (?,?,?)
            ON CONFLICT(company_id) DO UPDATE SET enabled=excluded.enabled,
                minutes_before=excluded.minutes_before,updated_at=now()
            """, companyId, settings.enabled(), settings.minutesBefore());
        audit.record(companyId, access.currentUserId(), "REMINDER_SETTINGS_UPDATED", "COMPANY", companyId, null, null);
        return settings;
    }
}
