package com.slotix.reservationcore.common;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceSecurityTest {
    @Test
    void rejectsUnsafeSecretsAndBoundsTokenLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("short", 15));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("x".repeat(64), 15));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(
            "slotix-dev-secret-key-cambiar-en-produccion-minimo-32-caracteres", 15));
        var jwt = new JwtService("Test-only-3ab9c028-f4d1-47e6-812a-9dc62f57e803", 15);
        var claims = jwt.parseAndValidate(jwt.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of("CUSTOMER")));
        assertEquals("slotix", claims.getIssuer());
        assertTrue(claims.getAudience().contains("slotix-api"));
        assertNotNull(UUID.fromString(claims.getId()));
    }
}
