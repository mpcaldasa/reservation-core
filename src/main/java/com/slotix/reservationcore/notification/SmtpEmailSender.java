package com.slotix.reservationcore.notification;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender sender;
    private final String from;

    public SmtpEmailSender(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.recipient());
        message.setSubject(email.subject());
        message.setText(email.body());
        try {
            sender.send(message);
        } catch (MailException exception) {
            throw new EmailDeliveryException(exception);
        }
    }
}
