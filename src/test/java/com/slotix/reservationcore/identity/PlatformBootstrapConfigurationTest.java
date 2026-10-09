package com.slotix.reservationcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformBootstrapConfigurationTest {
    private final PlatformBootstrapService service = mock(PlatformBootstrapService.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(PlatformBootstrapConfiguration.class)
        .withBean(PlatformBootstrapService.class, () -> service);

    @Test
    void bootstrapIsDisabledWithoutExplicitConfiguration() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).doesNotHaveBean(ApplicationRunner.class);
            assertThat(application).doesNotHaveBean(PlatformBootstrapProperties.class);
        });
    }

    @Test
    void enabledBootstrapBindsPropertiesAndInvokesTheInitializer() {
        context.withPropertyValues("app.bootstrap.platform-admin.enabled=true",
            "app.bootstrap.platform-admin.email=platform@example.test",
            "app.bootstrap.platform-admin.password=correct-horse-42").run(application -> {
                assertThat(application).hasNotFailed();
                var properties = application.getBean(PlatformBootstrapProperties.class);
                assertThat(properties.fullName()).isEqualTo("Platform Administrator");
                application.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments(new String[0]));
                verify(service).initialize(properties);
            });
    }
}
