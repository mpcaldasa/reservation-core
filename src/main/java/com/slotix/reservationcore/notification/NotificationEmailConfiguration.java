package com.slotix.reservationcore.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.notification.email.enabled", havingValue = "true")
@EnableConfigurationProperties(NotificationEmailProperties.class)
public class NotificationEmailConfiguration {
    @Bean
    EmailSender emailSender(JavaMailSender sender, NotificationEmailProperties properties) {
        return new SmtpEmailSender(sender, properties.from());
    }

    @Bean
    NotificationRetryPolicy notificationRetryPolicy() {
        return new NotificationRetryPolicy();
    }
}
