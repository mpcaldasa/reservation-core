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
