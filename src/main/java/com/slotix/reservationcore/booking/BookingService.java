package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.availability.AvailabilityService;
import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.AuthenticatedPrincipal;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.policy.BookingPolicy;
import com.slotix.reservationcore.notification.NotificationOutboxService;
import com.slotix.reservationcore.observability.SlotixMetrics;
import com.slotix.reservationcore.policy.BookingPolicyRepository;
import com.slotix.reservationcore.policy.ResourcePolicyRepository;
import com.slotix.reservationcore.resource.Resource;
import com.slotix.reservationcore.resource.ResourceAccessService;
import com.slotix.reservationcore.resource.ResourceStatus;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final BookingResourceRepository occupied;
    private final IdempotencyKeyService idempotencyKeys;
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;
    private final ResourceAccessService resources;
    private final ResourcePolicyRepository assignments;
    private final BookingPolicyRepository policies;
    private final AvailabilityService availability;
    private final TenantAccessService access;
    private final EntityManager entityManager;
    private final AuditService audit;
    private final NotificationOutboxService notifications;
    private final SlotixMetrics metrics;
    private final Clock clock;

    public BookingService(BookingRepository bookings, BookingResourceRepository occupied, IdempotencyKeyService idempotencyKeys,
                          CompanyRepository companies, CompanyMembershipRepository memberships, ResourceAccessService resources,
                          ResourcePolicyRepository assignments,
                          BookingPolicyRepository policies, AvailabilityService availability, TenantAccessService access,
                          EntityManager entityManager, AuditService audit, NotificationOutboxService notifications,
                          SlotixMetrics metrics, Clock clock) {
        this.bookings = bookings;
        this.occupied = occupied;
        this.idempotencyKeys = idempotencyKeys;
        this.companies = companies;
        this.memberships = memberships;
        this.resources = resources;
        this.assignments = assignments;
        this.policies = policies;
        this.availability = availability;
        this.access = access;
        this.entityManager = entityManager;
        this.audit = audit;
        this.notifications = notifications;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(UUID companyId, String key, CreateBookingRequest request) {
        access.requireActiveMembership(companyId);
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.IDEMPOTENCY_KEY_INVALID,
                "Idempotency-Key must contain between 1 and 200 characters");
        }
        if (!request.endAt().isAfter(request.startAt()) || Duration.between(request.startAt(), request.endAt()).toSeconds() % 60 != 0
            || Duration.between(request.startAt(), request.endAt()).toMinutes() > 1440) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.BOOKING_INTERVAL_INVALID,
                "The booking interval must have a positive whole-minute duration no longer than 24 hours");
        }
        AuthenticatedPrincipal principal = principal();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean staff = hasRole(authentication, "COMPANY_ADMIN") || hasRole(authentication, "BOOKING_MANAGER");
        UUID customerId = request.customerUserId() == null ? principal.userId() : request.customerUserId();
        if (!staff && !customerId.equals(principal.userId())) {
            throw new AccessDeniedException("Customers can only create their own bookings");
        }
        if (!memberships.existsByCompanyIdAndUserIdAndStatusAndDeletedAtIsNull(companyId, customerId, "ACTIVE")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.CUSTOMER_MEMBERSHIP_REQUIRED,
                "The customer must have an active membership in this company");
        }
        String hash = hash(request, customerId);

        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "The company must be active to accept bookings"));
        var existing = idempotencyKeys.findCurrent(companyId, key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This idempotency key was already used with a different booking request");
            }
            return BookingResponse.from(bookings.findByIdAndCompanyId(existing.get().getBookingId(), companyId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENT_BOOKING_NOT_FOUND,
                    "The booking associated with this idempotency key no longer exists")));
        }
        Resource resource = resources.requireVisibleResource(companyId, request.resourceId());
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.RESOURCE_INACTIVE,
                "The selected resource is not active");
        }
        BookingPolicy policy = assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(resource.getId()).stream()
            .filter(assignment -> assignment.covers(request.startAt(), request.endAt()))
            .findFirst()
            .flatMap(assignment -> policies.findByIdAndCompanyIdAndStatusAndDeletedAtIsNull(
                assignment.getPolicyId(), companyId, "ACTIVE"))
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.BOOKING_POLICY_UNAVAILABLE, "No active booking policy covers the requested interval"));
        int duration = (int) Duration.between(request.startAt(), request.endAt()).toMinutes();
        ZoneId zone = ZoneId.of(company.getTimezone());
        boolean validSlot = availability.slots(companyId, resource.getId(),
                request.startAt().atZone(zone).toLocalDate(), duration).stream()
            .anyMatch(slot -> slot.startAt().equals(request.startAt()) && slot.endAt().equals(request.endAt()));
        if (!validSlot || occupied.occupied(resource.getId(), request.startAt(), request.endAt()) >= resource.getCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_SLOT_UNAVAILABLE,
                "The selected time slot is no longer available");
        }
        Booking booking = bookings.saveAndFlush(Booking.create(companyId, customerId, resource.getId(), request.startAt(),
            request.endAt(), policy.isApprovalRequired() ? BookingStatus.PENDING : BookingStatus.CONFIRMED,
            company.getTimezone(), request.notes()));
        occupied.saveAndFlush(BookingResource.create(booking, resource.getCapacity() == 1));
        idempotencyKeys.create(companyId, key, hash, booking.getId());
        audit.record(companyId, principal.userId(), "BOOKING_CREATED", "BOOKING", booking.getId(), null,
            "{\"status\":\"" + booking.getStatus() + "\"}");
        notifications.enqueueBookingEvent(companyId, booking.getId(), "BOOKING_CREATED");
        metrics.bookingCreated(booking.getStatus());
        entityManager.refresh(booking);
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID companyId, UUID bookingId) {
        access.requireActiveMembership(companyId);
        Booking booking = bookings.findByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.BOOKING_NOT_FOUND,
                "The booking does not exist in this company"));
        requireViewPermission(booking);
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> calendar(UUID companyId, Instant from, Instant to) {
        access.requireActiveMembership(companyId);
        if (!to.isAfter(from) || Duration.between(from, to).toDays() > 31) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.CALENDAR_RANGE_INVALID,
                "The calendar range must be positive and no longer than 31 days");
        }
        AuthenticatedPrincipal actor = principal();
        boolean staff = isStaff();
        return bookings.inCalendar(companyId, from, to)
            .stream().filter(booking -> staff || booking.getCustomerUserId().equals(actor.userId()))
            .map(BookingResponse::from).toList();
    }

    @Transactional
    public BookingResponse cancel(UUID companyId, UUID bookingId, String reason) {
        access.requireActiveMembership(companyId);
        Booking booking = bookings.lockByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.BOOKING_NOT_FOUND,
                "The booking does not exist in this company"));
        requireViewPermission(booking);
        if (booking.getStatus() == BookingStatus.CANCELLED) return BookingResponse.from(booking);
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.BOOKING_STATE_INVALID,
                "The booking cannot be cancelled from its current status");
        }
        boolean staff = isStaff();
        if (staff && (reason == null || reason.isBlank())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.CANCELLATION_REASON_REQUIRED,
                "Staff cancellations must include a reason");
        }
        if (!staff) {
            BookingPolicy policy = assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(booking.getResourceId()).stream()
                .filter(item -> item.covers(booking.getStartAt(), booking.getEndAt())).findFirst()
                .flatMap(item -> policies.findByIdAndCompanyIdAndDeletedAtIsNull(item.getPolicyId(), companyId))
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    ApiErrorCode.BOOKING_POLICY_UNAVAILABLE, "The booking policy for this reservation is unavailable"));
            Instant cancellationDeadline = clock.instant()
                .plusSeconds(policy.getCancellationNoticeMinutes() * 60L);
            if (!policy.isAllowCustomerCancel() || booking.getStartAt().isBefore(cancellationDeadline)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    ApiErrorCode.CUSTOMER_CANCELLATION_NOT_ALLOWED,
                    "Customer cancellation is not allowed by the booking policy at this time");
            }
        }
        BookingStatus previousStatus = booking.getStatus();
        booking.cancel(principal().userId(), reason);
        bookings.flush();
        BookingResource row = occupied.findByIdBookingIdAndIdResourceId(bookingId, booking.getResourceId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_ALLOCATION_NOT_FOUND,
                "The booking allocation record is missing"));
        row.cancel();
        occupied.flush();
        audit.record(companyId, principal().userId(), "BOOKING_CANCELLED", "BOOKING", bookingId,
            "{\"status\":\"" + previousStatus + "\"}", "{\"status\":\"CANCELLED\"}");
        notifications.enqueueBookingEvent(companyId, bookingId, "BOOKING_CANCELLED");
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse decide(UUID companyId, UUID bookingId, BookingStatus decision) {
        access.requireActiveMembership(companyId);
        if (!isStaff()) throw new AccessDeniedException("Only company staff can decide pending bookings");
        Booking booking = bookings.lockByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.BOOKING_NOT_FOUND,
                "The booking does not exist in this company"));
        BookingStatus previous = booking.getStatus();
        try {
            booking.decide(decision);
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.BOOKING_STATE_INVALID,
                ex.getMessage());
        }
        bookings.flush();
        BookingResource allocation = occupied.findByIdBookingIdAndIdResourceId(bookingId, booking.getResourceId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_ALLOCATION_NOT_FOUND,
                "The booking allocation record is missing"));
        allocation.setStatus(decision);
        occupied.flush();
        String before = "{\"status\":\"" + previous + "\"}";
        String after = "{\"status\":\"" + decision + "\"}";
        audit.record(companyId, principal().userId(), "BOOKING_" + decision, "BOOKING", bookingId, before, after);
        notifications.enqueueBookingEvent(companyId, bookingId, "BOOKING_" + decision);
        return BookingResponse.from(booking);
    }

    private void requireViewPermission(Booking booking) {
        if (!isStaff() && !booking.getCustomerUserId().equals(principal().userId())) {
            throw new AccessDeniedException("You cannot access this booking");
        }
    }

    private boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return hasRole(authentication, "COMPANY_ADMIN") || hasRole(authentication, "BOOKING_MANAGER");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    private AuthenticatedPrincipal principal() {
        return (AuthenticatedPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String hash(CreateBookingRequest request, UUID customerId) {
        String value = request.resourceId() + "|" + customerId + "|" + request.startAt() + "|" + request.endAt()
            + "|" + (request.notes() == null ? "" : request.notes());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
