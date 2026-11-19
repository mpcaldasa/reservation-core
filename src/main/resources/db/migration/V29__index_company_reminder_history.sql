CREATE INDEX ix_notification_company_reminder_history
    ON notification_deliveries(company_id, created_at DESC, id DESC)
    WHERE template = 'BOOKING_REMINDER';
