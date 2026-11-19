package com.slotix.reservationcore.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.notification.email")
public record NotificationEmailProperties(
    @NotBlank @Email String from,
    @DefaultValue("5") @Min(1) @Max(20) int maxAttempts,
    @DefaultValue("10") @Min(1) @Max(100) int batchSize,
    @DefaultValue("120") @Min(30) @Max(3600) int leaseSeconds) {}
