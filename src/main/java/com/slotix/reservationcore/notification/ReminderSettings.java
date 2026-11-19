package com.slotix.reservationcore.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReminderSettings(boolean enabled, @Min(1) @Max(10080) int minutesBefore) {}
