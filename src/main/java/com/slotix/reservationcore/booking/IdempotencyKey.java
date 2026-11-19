package com.slotix.reservationcore.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    @Id @GeneratedValue private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "request_hash", nullable = false) private String requestHash;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected IdempotencyKey() {}

    public static IdempotencyKey create(UUID companyId, String key, String requestHash, UUID bookingId,
                                        Instant createdAt, Duration timeToLive) {
        IdempotencyKey record = new IdempotencyKey();
        record.companyId = companyId;
        record.idempotencyKey = key;
        record.requestHash = requestHash;
        record.bookingId = bookingId;
        record.createdAt = createdAt;
        record.expiresAt = createdAt.plus(timeToLive);
        return record;
    }

    public String getRequestHash() { return requestHash; }
    public UUID getBookingId() { return bookingId; }
}
