package com.slotix.reservationcore.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    @Id @GeneratedValue private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "idempotency_key", nullable = false) private String key;
    @Column(name = "request_hash", nullable = false) private String requestHash;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected IdempotencyKey() {}

    public static IdempotencyKey create(UUID companyId, String key, String requestHash, UUID bookingId) {
        IdempotencyKey record = new IdempotencyKey();
        record.companyId = companyId;
        record.key = key;
        record.requestHash = requestHash;
        record.bookingId = bookingId;
        record.createdAt = Instant.now();
        record.expiresAt = record.createdAt.plusSeconds(86_400);
        return record;
    }

    public String getRequestHash() { return requestHash; }
    public UUID getBookingId() { return bookingId; }
}
