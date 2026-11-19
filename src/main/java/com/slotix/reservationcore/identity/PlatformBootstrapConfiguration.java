package com.slotix.reservationcore.identity;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.bootstrap.platform-admin.enabled", havingValue = "true")
@EnableConfigurationProperties(PlatformBootstrapProperties.class)
public class PlatformBootstrapConfiguration {
    @Bean
    ApplicationRunner platformAdministratorBootstrap(PlatformBootstrapService service, PlatformBootstrapProperties properties) {
        return arguments -> service.initialize(properties);
    }
}
