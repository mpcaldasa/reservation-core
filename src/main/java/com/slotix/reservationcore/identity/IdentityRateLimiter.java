package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityRateLimiter {
    private final JdbcTemplate jdbc;

    public IdentityRateLimiter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acquire(String purpose, String identifier, int limit, int windowSeconds) {
        String bucket = IdentityToken.hash(purpose + ":" + identifier.trim().toLowerCase(Locale.ROOT));
        Integer count = jdbc.queryForObject("""
            INSERT INTO identity_rate_limits(bucket, window_start, attempts) VALUES (?, now(), 1)
            ON CONFLICT (bucket) DO UPDATE SET
                attempts = CASE WHEN identity_rate_limits.window_start <= now() - (? * interval '1 second')
                    THEN 1 ELSE identity_rate_limits.attempts + 1 END,
                window_start = CASE WHEN identity_rate_limits.window_start <= now() - (? * interval '1 second')
                    THEN now() ELSE identity_rate_limits.window_start END
            RETURNING attempts
            """, Integer.class, bucket, windowSeconds, windowSeconds);
        if (count == null || count > limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.RATE_LIMIT_EXCEEDED,
                "Too many requests; try again later");
        }
    }

    @Scheduled(fixedDelayString = "${app.identity.rate-limit-cleanup-ms:3600000}")
    public void cleanExpired() {
        jdbc.update("DELETE FROM identity_rate_limits WHERE window_start < now() - interval '1 day'");
    }
}
