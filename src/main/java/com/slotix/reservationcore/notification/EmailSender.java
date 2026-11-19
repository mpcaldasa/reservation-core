package com.slotix.reservationcore.notification;

public interface EmailSender {
    /** Throws EmailDeliveryException for an expected transport failure. */
    void send(EmailMessage message);
}
