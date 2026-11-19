package com.slotix.reservationcore.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationEmailConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(NotificationEmailConfiguration.class)
        .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class));

    @Test
    void disabledEmailDoesNotRequireSenderConfiguration() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).doesNotHaveBean(EmailSender.class);
            assertThat(application).doesNotHaveBean(NotificationEmailProperties.class);
        });
    }

    @Test
    void enabledEmailBindsValidatedDefaults() {
        context.withPropertyValues("app.notification.email.enabled=true", "app.notification.email.from=booking@slotix.test")
            .run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application).hasSingleBean(EmailSender.class);
                assertThat(application.getBean(NotificationEmailProperties.class))
                    .isEqualTo(new NotificationEmailProperties("booking@slotix.test", 5, 10, 120));
            });
    }

    @Test
    void enabledEmailRejectsInvalidConfiguration() {
        for (String invalid : new String[] { "from=", "from=invalid", "max-attempts=0", "batch-size=0", "lease-seconds=0" }) {
            context.withPropertyValues("app.notification.email.enabled=true", "app.notification.email.from=booking@slotix.test",
                "app.notification.email." + invalid).run(application -> {
                    assertThat(application).hasFailed();
                    assertThat(application.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
        }
    }
}
