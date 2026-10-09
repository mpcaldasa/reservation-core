package com.slotix.reservationcore.notification;

public record EmailMessage(String recipient, String subject, String body) {}
