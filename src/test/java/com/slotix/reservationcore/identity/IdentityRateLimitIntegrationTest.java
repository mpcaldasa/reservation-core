package com.slotix.reservationcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.slotix.reservationcore.common.ApiException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class IdentityRateLimitIntegrationTest extends IdentityIntegrationTestSupport {
    @Autowired private IdentityRateLimiter limiter;

    @Test
    void countsFailuresOutsideCallerTransactionsAndSerializesConcurrentAttempts() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        try (var executor = Executors.newFixedThreadPool(8)) {
            var requests = IntStream.range(0, 16).mapToObj(index -> executor.submit(() -> {
                try { limiter.acquire("test", email, 5, 300); return true; }
                catch (ApiException denied) { assertEquals(429, denied.getStatus().value()); return false; }
            })).toList();
            int accepted = 0;
            for (var request : requests) if (request.get()) accepted++;
            assertEquals(5, accepted);
        }
        assertThrows(ApiException.class, () -> limiter.acquire("test", email.toUpperCase(), 5, 300));
        jdbc.update("UPDATE identity_rate_limits SET window_start = now() - interval '10 minutes'");
        assertDoesNotThrow(() -> limiter.acquire("test", email, 5, 300));
        assertFalse(jdbc.queryForObject("SELECT bucket FROM identity_rate_limits LIMIT 1", String.class).contains(email));
    }
}
