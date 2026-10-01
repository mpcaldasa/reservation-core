package com.slotix.reservationcore.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_policies")
public class BookingPolicy {
    @Id @GeneratedValue private UUID id;
    @Column(name = "company_id") private UUID companyId;
    private String name;
    @Column(name = "min_duration_minutes") private int minDurationMinutes;
    @Column(name = "max_duration_minutes") private int maxDurationMinutes;
    @Column(name = "slot_increment_minutes") private int slotIncrementMinutes;
    @Column(name = "min_notice_minutes") private int minNoticeMinutes;
    @Column(name = "max_advance_days") private int maxAdvanceDays;
    @Column(name = "cancellation_notice_minutes") private int cancellationNoticeMinutes;
    @Column(name = "approval_required") private boolean approvalRequired;
    @Column(name = "allow_customer_cancel") private boolean allowCustomerCancel;
    private String status;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected BookingPolicy() {}

    public static BookingPolicy create(UUID companyId, String name, int min, int max, int increment, int notice,
                                       int advance, int cancellation, boolean approval, boolean customerCancel) {
        BookingPolicy policy = new BookingPolicy();
        policy.companyId = companyId;
        policy.name = name;
        policy.minDurationMinutes = min;
        policy.maxDurationMinutes = max;
        policy.slotIncrementMinutes = increment;
        policy.minNoticeMinutes = notice;
        policy.maxAdvanceDays = advance;
        policy.cancellationNoticeMinutes = cancellation;
        policy.approvalRequired = approval;
        policy.allowCustomerCancel = customerCancel;
        policy.status = "ACTIVE";
        policy.createdAt = Instant.now();
        policy.updatedAt = policy.createdAt;
        return policy;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public int getMinDurationMinutes() { return minDurationMinutes; }
    public int getMaxDurationMinutes() { return maxDurationMinutes; }
    public int getSlotIncrementMinutes() { return slotIncrementMinutes; }
    public int getMinNoticeMinutes() { return minNoticeMinutes; }
    public int getMaxAdvanceDays() { return maxAdvanceDays; }
    public int getCancellationNoticeMinutes() { return cancellationNoticeMinutes; }
    public boolean isApprovalRequired() { return approvalRequired; }
    public boolean isAllowCustomerCancel() { return allowCustomerCancel; }
    public String getStatus() { return status; }
    public Instant getDeletedAt() { return deletedAt; }
}
