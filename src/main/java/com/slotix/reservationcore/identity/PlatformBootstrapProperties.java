package com.slotix.reservationcore.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.bootstrap.platform-admin")
public record PlatformBootstrapProperties(String email, String password, @DefaultValue("Platform Administrator") String fullName) {
    @Override
    public String toString() {
        return "PlatformBootstrapProperties[email=" + email + ", password=[REDACTED], fullName=" + fullName + "]";
    }
}
