package com.slotix.reservationcore.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class IdempotencyKeyService {
    private final IdempotencyKeyRepository keys;
    private final Clock clock;
    private final Duration timeToLive;

    public IdempotencyKeyService(IdempotencyKeyRepository keys, Clock clock,
                                 @Value("${app.booking.idempotency.ttl:PT24H}") Duration timeToLive) {
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("Idempotency key time to live must be positive");
        }
        this.keys = keys;
        this.clock = clock;
        this.timeToLive = timeToLive;
    }

    public Optional<IdempotencyKey> findCurrent(UUID companyId, String key) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        keys.deleteExpired(companyId, key, now);
        return keys.findByCompanyIdAndIdempotencyKey(companyId, key);
    }

    public IdempotencyKey create(UUID companyId, String key, String requestHash, UUID bookingId) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return keys.save(IdempotencyKey.create(companyId, key, requestHash, bookingId, now, timeToLive));
    }
}
