package com.slotix.reservationcore.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.slotix.reservationcore.observability.SlotixMetrics;
import com.slotix.reservationcore.observability.ScheduledJobObserver;

import java.time.DateTimeException;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.notification.email.enabled", havingValue = "true")
public class EmailDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryWorker.class);

    private final NotificationDeliveryStore deliveries;
    private final EmailSender sender;
    private final BookingEmailTemplate template;
    private final NotificationRetryPolicy retries;
    private final NotificationEmailProperties properties;
    private final SlotixMetrics metrics;
    private final ScheduledJobObserver jobs;

    public EmailDeliveryWorker(NotificationDeliveryStore deliveries, EmailSender sender, BookingEmailTemplate template,
                               NotificationRetryPolicy retries, NotificationEmailProperties properties,
                               SlotixMetrics metrics, ScheduledJobObserver jobs) {
        this.deliveries = deliveries;
        this.sender = sender;
        this.template = template;
        this.retries = retries;
        this.properties = properties;
        this.metrics = metrics;
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${app.notification.email.delay-ms:5000}",
        initialDelayString = "${app.notification.email.initial-delay-ms:5000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendDueEmails() {
        jobs.observe("booking-email-delivery", this::sendBatch);
    }

    private void sendBatch() {
        for (int processed = 0; processed < properties.batchSize(); processed++) {
            var claimed = deliveries.claimNext(properties.maxAttempts(), Duration.ofSeconds(properties.leaseSeconds()));
            if (claimed.isEmpty()) {
                return;
            }
            deliver(claimed.get());
        }
    }

    private void deliver(NotificationDelivery delivery) {
        try {
            sender.send(template.render(delivery));
        } catch (EmailDeliveryException exception) {
            recordFailure(delivery, "SMTP_SEND_FAILED");
            return;
        } catch (IllegalArgumentException | DateTimeException exception) {
            recordFailure(delivery, "INVALID_NOTIFICATION_CONTENT");
            return;
        }
        if (!deliveries.markSent(delivery)) {
            log.warn("Notification delivery {} lost its claim before success was recorded", delivery.id());
            return;
        }
        metrics.notificationDelivery("booking", "sent");
    }

    private void recordFailure(NotificationDelivery delivery, String errorCode) {
        boolean recorded = deliveries.markFailed(delivery, errorCode, retries.delayAfter(delivery.attempt()));
        boolean exhausted = recorded && delivery.attempt() >= properties.maxAttempts();
        if (recorded) {
            metrics.notificationDelivery("booking", "failed");
            if (exhausted) metrics.notificationDelivery("booking", "exhausted");
        }
        log.atWarn()
            .addKeyValue("notificationChannel", "booking")
            .addKeyValue("notificationDeliveryId", delivery.id())
            .addKeyValue("notificationAttempt", delivery.attempt())
            .addKeyValue("notificationErrorCode", errorCode)
            .addKeyValue("notificationFailureRecorded", recorded)
            .addKeyValue("notificationExhausted", exhausted)
            .log("Notification delivery failed");
    }
}
