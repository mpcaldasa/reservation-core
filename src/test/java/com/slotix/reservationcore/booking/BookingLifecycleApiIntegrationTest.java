package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.PostgresIntegrationTestSupport;
import com.slotix.reservationcore.common.JwtService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.CompanyMembership;
import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.identity.UserRepository;
import com.slotix.reservationcore.notification.NotificationQueueWorker;
import com.slotix.reservationcore.resource.Resource;
import com.slotix.reservationcore.resource.ResourceRepository;
import com.slotix.reservationcore.resource.ResourceType;
import com.slotix.reservationcore.resource.ResourceVisibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingLifecycleApiIntegrationTest extends PostgresIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-10T14:15:00Z");
    private static final Instant START = NOW.minusSeconds(900);
    private static final Instant END = NOW.plusSeconds(900);

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JwtService jwt;
    @Autowired private CompanyRepository companies;
    @Autowired private UserRepository users;
    @Autowired private CompanyMembershipRepository memberships;
    @Autowired private ResourceRepository resources;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingResourceRepository allocations;
    @Autowired private NotificationQueueWorker queue;
    @Autowired private PendingBookingExpirationWorker expirationWorker;
    @Autowired private IdempotencyKeyCleanupWorker idempotencyKeyCleanupWorker;
    @MockitoBean private Clock clock;

    private final List<Fixture> fixtures = new ArrayList<>();

    @BeforeEach
    void configureClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void removeFixtures() {
        for (Fixture fixture : fixtures) {
            UUID companyId = fixture.companyId();
            jdbc.update("DELETE FROM notification_deliveries WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM outbox_events WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM audit_logs WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM booking_resources WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM idempotency_keys WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM bookings WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM resources WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM membership_roles WHERE membership_id IN (SELECT id FROM company_memberships WHERE company_id = ?)", companyId);
            jdbc.update("DELETE FROM company_memberships WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
            jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", fixture.customerId(), fixture.adminId(), fixture.managerId());
        }
    }

    @Test
    void staffCanCheckInAndCompleteWithoutDuplicatingEffects() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKED_IN"))
            .andExpect(jsonPath("$.checkedInAt").value(NOW.toString()))
            .andExpect(jsonPath("$.checkedInBy").value(fixture.adminId().toString()));
        mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.managerToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.checkedInBy").value(fixture.adminId().toString()));
        assertEquals(1, allocations.occupied(fixture.resourceId(), START, END));

        Instant completion = NOW.plusSeconds(60);
        when(clock.instant()).thenReturn(completion);
        mvc.perform(post(endpoint(fixture, "complete")).header("Authorization", fixture.managerToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completedAt").value(completion.toString()))
            .andExpect(jsonPath("$.completedBy").value(fixture.managerId().toString()));
        mvc.perform(post(endpoint(fixture, "complete")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.completedBy").value(fixture.managerId().toString()));

        assertEquals(0, allocations.occupied(fixture.resourceId(), START, END));
        assertEquals("COMPLETED", allocationStatus(fixture));
        assertEquals(2, auditCount(fixture));
        assertEquals(2, eventCount(fixture));
        queue.stagePendingDeliveries();
        assertEquals(Set.of("BOOKING_CHECKED_IN", "BOOKING_COMPLETED"), Set.copyOf(jdbc.queryForList(
            "SELECT template FROM notification_deliveries WHERE booking_id = ?", String.class, fixture.bookingId())));
    }

    @Test
    void customersAndOtherCompaniesCannotManageAttendance() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        Fixture other = fixture(BookingStatus.CONFIRMED);
        for (String action : List.of("check-in", "complete")) {
            mvc.perform(post(endpoint(fixture, action))).andExpect(status().isUnauthorized());
            mvc.perform(post(endpoint(fixture, action)).header("Authorization", fixture.customerToken()))
                .andExpect(status().isForbidden());
            mvc.perform(post(endpoint(fixture, action)).header("Authorization", other.adminToken()))
                .andExpect(status().isNotFound());
        }
        assertEquals(0, eventCount(fixture));
    }

    @Test
    void suspendedStaffCannotUseAnExistingToken() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        jdbc.update("UPDATE company_memberships SET status = 'SUSPENDED' WHERE company_id = ? AND user_id = ?",
            fixture.companyId(), fixture.adminId());
        mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isUnauthorized());
        assertEquals("CONFIRMED", bookingStatus(fixture));
    }

    @Test
    void checkInUsesTheBookingTimeWindow() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        for (Instant invalid : List.of(START.minusSeconds(1), END)) {
            when(clock.instant()).thenReturn(invalid);
            mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.adminToken()))
                .andExpect(status().isUnprocessableContent());
        }
        assertEquals(0, eventCount(fixture));
        when(clock.instant()).thenReturn(START);
        mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.checkedInAt").value(START.toString()));
    }

    @Test
    void pendingBookingsCannotCheckInOrComplete() throws Exception {
        Fixture fixture = fixture(BookingStatus.PENDING);
        for (String action : List.of("check-in", "complete")) {
            mvc.perform(post(endpoint(fixture, action)).header("Authorization", fixture.adminToken()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BOOKING_STATE_INVALID"));
        }
        assertEquals("PENDING", allocationStatus(fixture));
        assertEquals(0, auditCount(fixture));
        assertEquals(0, eventCount(fixture));
    }

    @Test
    void completionRequiresCheckInAndCheckedInBookingsCannotBeCancelled() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        mvc.perform(post(endpoint(fixture, "complete")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isUnprocessableContent());
        mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isOk());
        mvc.perform(post(endpoint(fixture, "cancel")).header("Authorization", fixture.adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Staff cancellation\"}"))
            .andExpect(status().isUnprocessableContent());
        assertEquals("CHECKED_IN", bookingStatus(fixture));
        assertEquals("CHECKED_IN", allocationStatus(fixture));
        assertEquals(1, eventCount(fixture));
    }

    @Test
    void missingAllocationRollsBackTheAttendanceChange() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        jdbc.update("DELETE FROM booking_resources WHERE booking_id = ?", fixture.bookingId());
        mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BOOKING_ALLOCATION_NOT_FOUND"));
        assertEquals("CONFIRMED", bookingStatus(fixture));
        assertNull(bookings.findById(fixture.bookingId()).orElseThrow().getCheckedInAt());
        assertEquals(0, auditCount(fixture));
        assertEquals(0, eventCount(fixture));
    }

    @Test
    void concurrentCheckInsProduceOneAuditAndOneEvent() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        assertEquals(List.of(200, 200), concurrentActions(fixture, "check-in", "check-in"));
        assertEquals("CHECKED_IN", bookingStatus(fixture));
        assertEquals("CHECKED_IN", allocationStatus(fixture));
        assertEquals(1, auditCount(fixture));
        assertEquals(1, eventCount(fixture));
    }

    @Test
    void checkInAndCancellationCannotBothSucceed() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        assertEquals(Set.of(200, 422), Set.copyOf(concurrentActions(fixture, "check-in", "cancel")));
        assertEquals(bookingStatus(fixture), allocationStatus(fixture));
        assertEquals(1, auditCount(fixture));
        assertEquals(1, eventCount(fixture));
    }

    @Test
    void databaseProtectsAttendanceMetadata() {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "UPDATE bookings SET status = 'CHECKED_IN', checked_in_at = ? WHERE id = ?",
            Timestamp.from(NOW), fixture.bookingId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            UPDATE bookings SET status = 'COMPLETED', checked_in_at = ?, checked_in_by = ?,
                completed_at = ?, completed_by = ? WHERE id = ?
            """, Timestamp.from(NOW), fixture.adminId(), Timestamp.from(NOW.minusSeconds(1)),
            fixture.managerId(), fixture.bookingId()));
        assertEquals("CONFIRMED", bookingStatus(fixture));
    }

    @Test
    void attendanceTimesSurviveDatabaseRoundTrips() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        when(clock.instant()).thenReturn(NOW.plusNanos(123456789));
        String checkedInAt = "2026-10-10T14:15:00.123456Z";
        mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.checkedInAt").value(checkedInAt));
        mvc.perform(post(endpoint(fixture, "check-in")).header("Authorization", fixture.managerToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.checkedInAt").value(checkedInAt));

        when(clock.instant()).thenReturn(NOW.plusSeconds(60).plusNanos(987654321));
        String completedAt = "2026-10-10T14:16:00.987654Z";
        mvc.perform(post(endpoint(fixture, "complete")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").value(completedAt));
        mvc.perform(post(endpoint(fixture, "complete")).header("Authorization", fixture.managerToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").value(completedAt));
    }

    @Test
    void pendingExpirationUsesTheExactCutoffAndStagesOneNotification() {
        Fixture due = fixture(BookingStatus.PENDING);
        Fixture fresh = fixture(BookingStatus.PENDING);
        jdbc.update("UPDATE bookings SET created_at = ? WHERE id = ?", Timestamp.from(NOW.minusSeconds(1800)), due.bookingId());
        jdbc.update("UPDATE bookings SET created_at = ? WHERE id = ?", Timestamp.from(NOW.minusSeconds(1799)), fresh.bookingId());

        expirationWorker.expirePendingBookings();

        assertEquals("EXPIRED", bookingStatus(due));
        assertEquals("EXPIRED", allocationStatus(due));
        assertEquals("PENDING", bookingStatus(fresh));
        assertEquals(1, auditCount(due));
        assertEquals(1, eventCount(due));
        assertNull(jdbc.queryForObject("SELECT actor_user_id FROM audit_logs WHERE entity_id = ?", UUID.class,
            due.bookingId()));

        queue.stagePendingDeliveries();
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM notification_deliveries WHERE booking_id = ? AND template = 'BOOKING_EXPIRED'",
            Long.class, due.bookingId()));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM notification_deliveries WHERE booking_id = ?",
            Long.class, fresh.bookingId()));
    }

    @Test
    void idempotencyCleanupDeletesKeysAtTheExpiryBoundaryOnly() {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        UUID expiredId = UUID.randomUUID();
        UUID freshId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO idempotency_keys (id, company_id, idempotency_key, request_hash, booking_id, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?)
            """, expiredId, fixture.companyId(), "expired-key", "hash-expired", fixture.bookingId(),
            Timestamp.from(NOW.minusSeconds(86400)), Timestamp.from(NOW),
            freshId, fixture.companyId(), "fresh-key", "hash-fresh", fixture.bookingId(),
            Timestamp.from(NOW.minusSeconds(86399)), Timestamp.from(NOW.plusNanos(1000)));

        assertTrue(idempotencyKeyCleanupWorker.deleteExpiredKeys() >= 1);
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE id = ?", Long.class, expiredId));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE id = ?", Long.class, freshId));
    }

    @Test
    void concurrentExpirationPassesProduceOnlyOneTransitionAndNotification() throws Exception {
        Fixture fixture = fixture(BookingStatus.PENDING);
        jdbc.update("UPDATE bookings SET created_at = ? WHERE id = ?", Timestamp.from(NOW.minusSeconds(1800)),
            fixture.bookingId());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> expireAfterStart(start));
            var second = executor.submit(() -> expireAfterStart(start));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertEquals("EXPIRED", bookingStatus(fixture));
        assertEquals("EXPIRED", allocationStatus(fixture));
        assertEquals(1, auditCount(fixture));
        assertEquals(1, eventCount(fixture));
    }

    @Test
    void noShowRequiresTheBookingEndAndReleasesCapacityOnce() throws Exception {
        Fixture fixture = fixture(BookingStatus.CONFIRMED);
        when(clock.instant()).thenReturn(END.minusSeconds(1));
        mvc.perform(post(endpoint(fixture, "no-show")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("BOOKING_STATE_INVALID"));
        assertEquals("CONFIRMED", bookingStatus(fixture));
        assertEquals(0, auditCount(fixture));

        when(clock.instant()).thenReturn(END);
        mvc.perform(post(endpoint(fixture, "no-show")).header("Authorization", fixture.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"))
            .andExpect(jsonPath("$.noShowAt").value(END.toString()))
            .andExpect(jsonPath("$.noShowBy").value(fixture.adminId().toString()));
        mvc.perform(post(endpoint(fixture, "no-show")).header("Authorization", fixture.managerToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.noShowBy").value(fixture.adminId().toString()));

        assertEquals("NO_SHOW", allocationStatus(fixture));
        assertEquals(0, allocations.occupied(fixture.resourceId(), START, END));
        assertEquals(1, auditCount(fixture));
        assertEquals(1, eventCount(fixture));
        queue.stagePendingDeliveries();
        assertEquals(1L, jdbc.queryForObject(
            "SELECT count(*) FROM notification_deliveries WHERE booking_id = ? AND template = 'BOOKING_NO_SHOW'",
            Long.class, fixture.bookingId()));
    }

    private Void expireAfterStart(CountDownLatch start) throws InterruptedException {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        expirationWorker.expirePendingBookings();
        return null;
    }

    private List<Integer> concurrentActions(Fixture fixture, String firstAction, String secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> actionAfterStart(fixture, firstAction, ready, start));
            var second = executor.submit(() -> actionAfterStart(fixture, secondAction, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private int actionAfterStart(Fixture fixture, String action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        var request = post(endpoint(fixture, action)).header("Authorization", fixture.adminToken());
        if (action.equals("cancel")) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Staff cancellation\"}");
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private Fixture fixture(BookingStatus initialStatus) {
        Fixture fixture = transactions.execute(ignored -> {
            Company company = Company.create("Attendance Company", "Attendance Company",
                "attendance-" + UUID.randomUUID(), "attendance@example.test");
            company.activate();
            company = companies.save(company);
            User customer = member(company.getId(), MembershipRole.CUSTOMER);
            User admin = member(company.getId(), MembershipRole.COMPANY_ADMIN);
            User manager = member(company.getId(), MembershipRole.BOOKING_MANAGER);
            Resource resource = Resource.create(company.getId(), "Court", null, ResourceType.SPACE, 1, ResourceVisibility.MEMBERS);
            resource.activate();
            resource = resources.save(resource);
            Booking booking = bookings.saveAndFlush(Booking.create(company.getId(), customer.getId(), resource.getId(),
                START, END, initialStatus, "America/Bogota", null));
            allocations.saveAndFlush(BookingResource.create(booking, true));
            return new Fixture(company.getId(), booking.getId(), resource.getId(), customer.getId(), admin.getId(), manager.getId(),
                token(customer, company, MembershipRole.CUSTOMER), token(admin, company, MembershipRole.COMPANY_ADMIN),
                token(manager, company, MembershipRole.BOOKING_MANAGER));
        });
        fixtures.add(fixture);
        return fixture;
    }

    private User member(UUID companyId, MembershipRole role) {
        User user = users.save(User.create(UUID.randomUUID() + "@example.test", "unused", "Attendance " + role));
        memberships.save(CompanyMembership.create(companyId, user.getId(), Set.of(role)));
        return user;
    }

    private String token(User user, Company company, MembershipRole role) {
        return "Bearer " + jwt.generateToken(user.getId(), company.getId(), List.of(role.name()));
    }

    private String endpoint(Fixture fixture, String action) {
        return "/api/v1/bookings/" + fixture.bookingId() + "/" + action;
    }

    private String bookingStatus(Fixture fixture) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, fixture.bookingId());
    }

    private String allocationStatus(Fixture fixture) {
        return jdbc.queryForObject("SELECT status FROM booking_resources WHERE booking_id = ?", String.class, fixture.bookingId());
    }

    private int auditCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?", Integer.class, fixture.bookingId());
    }

    private int eventCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, fixture.bookingId());
    }

    private record Fixture(UUID companyId, UUID bookingId, UUID resourceId, UUID customerId, UUID adminId, UUID managerId,
                           String customerToken, String adminToken, String managerToken) {}
}
