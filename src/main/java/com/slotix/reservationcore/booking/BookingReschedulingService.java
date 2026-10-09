package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.availability.AvailabilityService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.notification.NotificationOutboxService;
import com.slotix.reservationcore.policy.BookingPolicyRepository;
import com.slotix.reservationcore.policy.ResourcePolicyRepository;
import com.slotix.reservationcore.resource.ResourceAccessService;
import com.slotix.reservationcore.resource.ResourceStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional
public class BookingReschedulingService {
    private final BookingRepository bookings;
    private final BookingResourceRepository allocations;
    private final CompanyRepository companies;
    private final ResourceAccessService resources;
    private final ResourcePolicyRepository assignments;
    private final BookingPolicyRepository policies;
    private final AvailabilityService availability;
    private final TenantAccessService access;
    private final AuditService audit;
    private final NotificationOutboxService notifications;
    private final Clock clock;

    public BookingReschedulingService(BookingRepository bookings, BookingResourceRepository allocations,
                                      CompanyRepository companies, ResourceAccessService resources,
                                      ResourcePolicyRepository assignments, BookingPolicyRepository policies,
                                      AvailabilityService availability, TenantAccessService access,
                                      AuditService audit, NotificationOutboxService notifications, Clock clock) {
        this.bookings = bookings;
        this.allocations = allocations;
        this.companies = companies;
        this.resources = resources;
        this.assignments = assignments;
        this.policies = policies;
        this.availability = availability;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER','CUSTOMER')")
    public BookingResponse reschedule(UUID companyId, UUID bookingId, RescheduleBookingRequest request) {
        access.requireActiveMembership(companyId);
        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "The company must be active to reschedule bookings"));
        var roles = access.currentCompanyRoles(companyId);
        Booking booking = bookings.lockByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.BOOKING_NOT_FOUND,
                "The booking does not exist in this company"));

        UUID actor = access.currentUserId();
        boolean staff = roles.contains(MembershipRole.COMPANY_ADMIN) || roles.contains(MembershipRole.BOOKING_MANAGER);
        if (!staff && !booking.getCustomerUserId().equals(actor)) {
            throw new AccessDeniedException("You cannot reschedule this booking");
        }

        BookingStatus previousStatus = booking.getStatus();
        if (previousStatus != BookingStatus.PENDING && previousStatus != BookingStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.BOOKING_STATE_INVALID,
                "Only pending or confirmed bookings can be rescheduled");
        }

        Instant startAt = request.startAt().truncatedTo(ChronoUnit.MICROS);
        Instant endAt = request.endAt().truncatedTo(ChronoUnit.MICROS);
        Duration duration = Duration.between(startAt, endAt);
        if (duration.isNegative() || duration.isZero() || duration.toSeconds() % 60 != 0
            || duration.toMinutes() > 1440) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.RESCHEDULE_INTERVAL_INVALID,
                "The rescheduled interval must have a positive whole-minute duration no longer than 24 hours");
        }

        Instant oldStartAt = booking.getStartAt();
        Instant oldEndAt = booking.getEndAt();
        if (!staff) {
            requireCustomerReschedulingAllowed(companyId, booking, oldStartAt, oldEndAt);
        }
        if (oldStartAt.equals(startAt) && oldEndAt.equals(endAt)) {
            return BookingResponse.from(booking);
        }

        var resource = resources.requireVisibleResource(companyId, booking.getResourceId());
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.RESOURCE_INACTIVE,
                "The booking resource is not active");
        }
        var policy = assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(resource.getId()).stream()
            .filter(assignment -> assignment.covers(startAt, endAt)).findFirst()
            .flatMap(assignment -> policies.findByIdAndCompanyIdAndStatusAndDeletedAtIsNull(
                assignment.getPolicyId(), companyId, "ACTIVE"))
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.BOOKING_POLICY_UNAVAILABLE, "No active booking policy covers the requested interval"));
        int durationMinutes = Math.toIntExact(duration.toMinutes());
        if (durationMinutes < policy.getMinDurationMinutes() || durationMinutes > policy.getMaxDurationMinutes()
            || durationMinutes % policy.getSlotIncrementMinutes() != 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.RESCHEDULE_POLICY_CONSTRAINT_VIOLATION,
                "The rescheduled duration does not satisfy the active booking policy");
        }

        ZoneId zone = ZoneId.of(company.getTimezone());
        boolean available = availability.slots(companyId, resource.getId(), startAt.atZone(zone).toLocalDate(),
                durationMinutes, bookingId).stream()
            .anyMatch(slot -> slot.startAt().equals(startAt) && slot.endAt().equals(endAt));
        if (!available) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_SLOT_UNAVAILABLE,
                "The selected time slot is no longer available");
        }

        booking.reschedule(startAt, endAt, currentTime());
        bookings.flush();
        BookingResource allocation = allocations.findByIdBookingIdAndIdResourceId(bookingId, booking.getResourceId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_ALLOCATION_NOT_FOUND,
                "The booking allocation record is missing"));
        if (allocation.getStatus() != previousStatus) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_ALLOCATION_STATE_MISMATCH,
                "The booking and allocation statuses are inconsistent");
        }
        allocation.reschedule(startAt, endAt);
        allocations.flush();

        audit.record(companyId, actor, "BOOKING_RESCHEDULED", "BOOKING", bookingId,
            "{\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(oldStartAt, oldEndAt),
            "{\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(startAt, endAt));
        notifications.enqueueBookingEvent(companyId, bookingId, "BOOKING_RESCHEDULED");
        return BookingResponse.from(booking);
    }

    private void requireCustomerReschedulingAllowed(UUID companyId, Booking booking, Instant startAt, Instant endAt) {
        var oldPolicy = assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(booking.getResourceId()).stream()
            .filter(assignment -> assignment.covers(startAt, endAt)).findFirst()
            .flatMap(assignment -> policies.findByIdAndCompanyIdAndDeletedAtIsNull(
                assignment.getPolicyId(), companyId))
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.BOOKING_POLICY_UNAVAILABLE, "The booking policy for this reservation is unavailable"));
        Instant reschedulingDeadline = startAt.minusSeconds(oldPolicy.getCancellationNoticeMinutes() * 60L);
        if (!oldPolicy.isAllowCustomerCancel() || currentTime().isAfter(reschedulingDeadline)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.CUSTOMER_RESCHEDULING_NOT_ALLOWED,
                "Customer rescheduling is not allowed by the booking policy at this time");
        }
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
