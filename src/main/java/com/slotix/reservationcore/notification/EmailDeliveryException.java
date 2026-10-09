package com.slotix.reservationcore.notification;

public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(Throwable cause) {
        super("SMTP delivery failed", cause);
    }
}
