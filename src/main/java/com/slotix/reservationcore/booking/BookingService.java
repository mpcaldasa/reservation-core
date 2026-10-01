package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.availability.AvailabilityService;
import com.slotix.reservationcore.common.AuthenticatedPrincipal;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.policy.BookingPolicy;
import com.slotix.reservationcore.policy.BookingPolicyRepository;
import com.slotix.reservationcore.policy.ResourcePolicyRepository;
import com.slotix.reservationcore.resource.Resource;
import com.slotix.reservationcore.resource.ResourceRepository;
import com.slotix.reservationcore.resource.ResourceStatus;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final IdempotencyKeyRepository keys;
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;
    private final ResourceRepository resources;
    private final ResourcePolicyRepository assignments;
    private final BookingPolicyRepository policies;
    private final AvailabilityService availability;
    private final TenantAccessService access;
    private final EntityManager entityManager;

    public BookingService(BookingRepository bookings, BookingResourceRepository occupied, IdempotencyKeyRepository keys,
                          CompanyRepository companies, CompanyMembershipRepository memberships, ResourceRepository resources,
                          ResourcePolicyRepository assignments,
                          BookingPolicyRepository policies, AvailabilityService availability, TenantAccessService access,
                          EntityManager entityManager) {
        this.bookings = bookings;
        this.occupied = occupied;
        this.keys = keys;
        this.companies = companies;
        this.memberships = memberships;
        this.resources = resources;
        this.assignments = assignments;
        this.policies = policies;
        this.availability = availability;
        this.access = access;
        this.entityManager = entityManager;
    }

    @Transactional
    public BookingResponse create(UUID companyId, String key, CreateBookingRequest request) {
        access.requireActiveMembership(companyId);
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 200 characters");
        }
        if (!request.endAt().isAfter(request.startAt()) || Duration.between(request.startAt(), request.endAt()).toSeconds() % 60 != 0
            || Duration.between(request.startAt(), request.endAt()).toMinutes() > 1440) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Booking interval must have a positive whole-minute duration");
        }
        AuthenticatedPrincipal principal = principal();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean staff = hasRole(authentication, "COMPANY_ADMIN") || hasRole(authentication, "BOOKING_MANAGER");
        UUID customerId = request.customerUserId() == null ? principal.userId() : request.customerUserId();
        if (!staff && !customerId.equals(principal.userId())) {
            throw new AccessDeniedException("Customers can only create their own bookings");
        }
        if (!memberships.existsByCompanyIdAndUserIdAndStatusAndDeletedAtIsNull(companyId, customerId, "ACTIVE")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Customer must be an active company member");
        }
        String hash = hash(request, customerId);

        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Company is not active"));
        var existing = keys.findByCompanyIdAndKey(companyId, key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED");
            }
            return BookingResponse.from(bookings.findByIdAndCompanyId(existing.get().getBookingId(), companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Idempotent booking not found")));
        }
        Resource resource = resources.findByIdAndCompanyIdAndDeletedAtIsNull(request.resourceId(), companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Resource is not active");
        }
        BookingPolicy policy = assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(resource.getId()).stream()
            .filter(assignment -> assignment.covers(request.startAt(), request.endAt()))
            .findFirst()
            .flatMap(assignment -> policies.findByIdAndCompanyIdAndStatusAndDeletedAtIsNull(
                assignment.getPolicyId(), companyId, "ACTIVE"))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "No active policy covers the booking"));
        int duration = (int) Duration.between(request.startAt(), request.endAt()).toMinutes();
        ZoneId zone = ZoneId.of(company.getTimezone());
        boolean validSlot = availability.slots(companyId, resource.getId(),
                request.startAt().atZone(zone).toLocalDate(), duration).stream()
            .anyMatch(slot -> slot.startAt().equals(request.startAt()) && slot.endAt().equals(request.endAt()));
        if (!validSlot || occupied.occupied(resource.getId(), request.startAt(), request.endAt()) >= resource.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "BOOKING_CONFLICT");
        }
        Booking booking = bookings.saveAndFlush(Booking.create(companyId, customerId, resource.getId(), request.startAt(),
            request.endAt(), policy.isApprovalRequired() ? BookingStatus.PENDING : BookingStatus.CONFIRMED,
            company.getTimezone(), request.notes()));
        occupied.saveAndFlush(BookingResource.create(booking, resource.getCapacity() == 1));
        keys.save(IdempotencyKey.create(companyId, key, hash, booking.getId()));
        entityManager.refresh(booking);
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID companyId, UUID bookingId) {
        access.requireActiveMembership(companyId);
        Booking booking = bookings.findByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        requireViewPermission(booking);
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> calendar(UUID companyId, Instant from, Instant to) {
        access.requireActiveMembership(companyId);
        if (!to.isAfter(from) || Duration.between(from, to).toDays() > 31) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calendar range must be positive and no longer than 31 days");
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
        Booking booking = bookings.findByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        requireViewPermission(booking);
        if (booking.getStatus() == BookingStatus.CANCELLED) return BookingResponse.from(booking);
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Booking cannot be cancelled from its current status");
        }
        boolean staff = isStaff();
        if (staff && (reason == null || reason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Staff cancellation requires a reason");
        }
        if (!staff) {
            BookingPolicy policy = assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(booking.getResourceId()).stream()
                .filter(item -> item.covers(booking.getStartAt(), booking.getEndAt())).findFirst()
                .flatMap(item -> policies.findByIdAndCompanyIdAndStatusAndDeletedAtIsNull(item.getPolicyId(), companyId, "ACTIVE"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Booking policy is unavailable"));
            if (!policy.isAllowCustomerCancel() || booking.getStartAt().isBefore(Instant.now().plusSeconds(policy.getCancellationNoticeMinutes() * 60L))) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Customer cancellation is outside the policy limit");
            }
        }
        booking.cancel(principal().userId(), reason);
        bookings.flush();
        BookingResource row = occupied.findByIdBookingIdAndIdResourceId(bookingId, booking.getResourceId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Booking allocation not found"));
        row.cancel();
        occupied.flush();
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
