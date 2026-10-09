package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.notification.EmailDeliveryException;
import com.slotix.reservationcore.notification.EmailMessage;
import com.slotix.reservationcore.notification.EmailSender;
import com.slotix.reservationcore.notification.NotificationEmailProperties;
import com.slotix.reservationcore.notification.NotificationRetryPolicy;
import com.slotix.reservationcore.observability.SlotixMetrics;
import com.slotix.reservationcore.observability.ScheduledJobObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.notification.email.enabled", havingValue = "true")
public class IdentityEmailWorker {
    private static final Logger logger = LoggerFactory.getLogger(IdentityEmailWorker.class);

    private final IdentityEmailDeliveryStore deliveries;
    private final EmailSender sender;
    private final NotificationRetryPolicy retries;
    private final NotificationEmailProperties properties;
    private final String invitationUrl;
    private final String passwordResetUrl;
    private final SlotixMetrics metrics;
    private final ScheduledJobObserver jobs;

    public IdentityEmailWorker(IdentityEmailDeliveryStore deliveries, EmailSender sender,
                               NotificationRetryPolicy retries, NotificationEmailProperties properties,
                               @Value("${app.identity.invitation.acceptance-url:http://localhost:3000/accept-invitation}")
                               String invitationUrl,
                               @Value("${app.identity.password-reset.acceptance-url:http://localhost:3000/reset-password}")
                               String passwordResetUrl, SlotixMetrics metrics, ScheduledJobObserver jobs) {
        this.deliveries = deliveries;
        this.sender = sender;
        this.retries = retries;
        this.properties = properties;
        this.invitationUrl = invitationUrl;
        this.passwordResetUrl = passwordResetUrl;
        this.metrics = metrics;
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${app.notification.email.delay-ms:5000}",
        initialDelayString = "${app.notification.email.initial-delay-ms:5000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendDueEmails() {
        jobs.observe("identity-email-delivery", this::sendBatch);
    }

    private void sendBatch() {
        for (int processed = 0; processed < properties.batchSize(); processed++) {
            var claimed = deliveries.claimNext(properties.maxAttempts(), Duration.ofSeconds(properties.leaseSeconds()));
            if (claimed.isEmpty()) return;
            deliver(claimed.get());
        }
    }

    private void deliver(IdentityEmailDelivery delivery) {
        try {
            sender.send(render(delivery));
        } catch (EmailDeliveryException exception) {
            boolean recorded = deliveries.markFailed(delivery, "SMTP_SEND_FAILED", retries.delayAfter(delivery.attempt()));
            boolean exhausted = recorded && delivery.attempt() >= properties.maxAttempts();
            if (recorded) {
                metrics.notificationDelivery("identity", "failed");
                if (exhausted) metrics.notificationDelivery("identity", "exhausted");
            }
            logger.atWarn()
                .addKeyValue("notificationChannel", "identity")
                .addKeyValue("notificationDeliveryId", delivery.id())
                .addKeyValue("notificationAttempt", delivery.attempt())
                .addKeyValue("notificationErrorCode", "SMTP_SEND_FAILED")
                .addKeyValue("notificationFailureRecorded", recorded)
                .addKeyValue("notificationExhausted", exhausted)
                .log("Identity email delivery failed");
            return;
        }
        if (!deliveries.markSent(delivery)) {
            logger.warn("Identity email delivery {} lost its claim before success was recorded", delivery.id());
            return;
        }
        metrics.notificationDelivery("identity", "sent");
    }

    private EmailMessage render(IdentityEmailDelivery delivery) {
        String link;
        String subject;
        String body;
        if ("COMPANY_INVITATION".equals(delivery.purpose())) {
            link = withToken(invitationUrl, delivery.token());
            subject = "You are invited to Slotix";
            body = "You have been invited to join %s on Slotix.\n\nAccept the invitation within 7 days:\n%s"
                .formatted(delivery.companyName(), link);
        } else if ("PASSWORD_RESET".equals(delivery.purpose())) {
            link = withToken(passwordResetUrl, delivery.token());
            subject = "Reset your Slotix password";
            body = "A password reset was requested for your Slotix account.\n\nUse this link within 30 minutes:\n%s"
                .formatted(link);
        } else {
            throw new IllegalStateException("Unsupported identity email purpose: " + delivery.purpose());
        }
        return new EmailMessage(delivery.recipient(), subject, body);
    }

    private static String withToken(String url, String token) {
        return url + (url.contains("?") ? "&" : "?") + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
