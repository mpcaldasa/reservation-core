package com.slotix.reservationcore.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotix.reservationcore.PostgresIntegrationTestSupport;
import com.slotix.reservationcore.availability.AvailabilityRule;
import com.slotix.reservationcore.availability.AvailabilityRuleRepository;
import com.slotix.reservationcore.availability.ResourceBlockRepository;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.common.JwtService;
import com.slotix.reservationcore.identity.CompanyMembership;
import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.identity.UserRepository;
import com.slotix.reservationcore.notification.NotificationQueueWorker;
import com.slotix.reservationcore.policy.BookingPolicy;
import com.slotix.reservationcore.policy.BookingPolicyRepository;
import com.slotix.reservationcore.policy.ResourcePolicy;
import com.slotix.reservationcore.policy.ResourcePolicyRepository;
import com.slotix.reservationcore.resource.Resource;
import com.slotix.reservationcore.resource.ResourceRepository;
import com.slotix.reservationcore.resource.ResourceType;
import com.slotix.reservationcore.resource.ResourceVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingApiIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    @Autowired private JwtService jwt;
    @Autowired private CompanyRepository companies;
    @Autowired private UserRepository users;
    @Autowired private CompanyMembershipRepository memberships;
    @Autowired private ResourceRepository resources;
    @Autowired private BookingPolicyRepository policies;
    @Autowired private ResourcePolicyRepository assignments;
    @Autowired private AvailabilityRuleRepository rules;
    @Autowired private ResourceBlockRepository blocks;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingResourceRepository bookingResources;
    @Autowired private IdempotencyKeyRepository keys;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationQueueWorker queueWorker;

    @BeforeEach
    void clean() {
        jdbc.update("delete from notification_deliveries");
        jdbc.update("delete from outbox_events");
        jdbc.update("delete from audit_logs");
        keys.deleteAll();
        bookingResources.deleteAll();
        bookings.deleteAll();
        blocks.deleteAll();
        rules.deleteAll();
        assignments.deleteAll();
        policies.deleteAll();
        resources.deleteAll();
        memberships.deleteAll();
        users.deleteAll();
        companies.deleteAll();
    }

    @Test
    void creationIsIdempotentAndCancellationReleasesCapacity() throws Exception {
        Fixture fixture = fixture(1);
        String request = body(fixture);
        String first = mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", "same-key").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.bookingNumber").isNumber())
            .andReturn().getResponse().getContentAsString();
        UUID bookingId = UUID.fromString(json.readTree(first).get("id").asText());
        mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", "same-key").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(bookingId.toString()));
        mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", "same-key").contentType(MediaType.APPLICATION_JSON)
                .content(request.replace("\"notes\":\"Original\"", "\"notes\":\"Changed\"")))
            .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", "other-key").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId).header("Authorization", bearer(fixture.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", "after-cancel").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated());
        assertEquals(2, bookings.count());
        assertEquals(3L, jdbc.queryForObject("select count(*) from audit_logs", Long.class));
        assertEquals(3L, jdbc.queryForObject("select count(*) from outbox_events", Long.class));
        queueWorker.stagePendingDeliveries();
        assertEquals(3L, jdbc.queryForObject("select count(*) from notification_deliveries where status='PENDING'", Long.class));
    }

    @Test
    void concurrentRequestsCannotDoubleBookSingleCapacityResource() throws Exception {
        Fixture fixture = fixture(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> createConcurrently(fixture, "first", ready, start));
            Future<Integer> second = executor.submit(() -> createConcurrently(fixture, "second", ready, start));
            ready.await();
            start.countDown();
            assertNotEquals(first.get(), second.get());
            assertEquals(Set.of(201, 409), Set.of(first.get(), second.get()));
        }
        assertEquals(1, bookings.count());
    }

    @Test
    void multipleCapacityAllowsTwoBookingsButRejectsThird() throws Exception {
        Fixture fixture = fixture(2);
        for (int index = 0; index < 3; index++) {
            mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                    .header("Idempotency-Key", "capacity-" + index)
                    .contentType(MediaType.APPLICATION_JSON).content(body(fixture)))
                .andExpect(index < 2 ? status().isCreated() : status().isConflict());
        }
        assertEquals(2, bookings.count());
    }

    @Test
    void postgresExclusionRejectsOverlapEvenWhenApplicationCheckIsBypassed() throws Exception {
        Fixture fixture = fixture(1);
        mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", "first").contentType(MediaType.APPLICATION_JSON).content(body(fixture)))
            .andExpect(status().isCreated());

        assertThrows(DataIntegrityViolationException.class, () -> transactions.executeWithoutResult(ignored -> {
            Booking second = bookings.saveAndFlush(Booking.create(fixture.companyId(), fixture.userId(),
                fixture.resourceId(), fixture.start(), fixture.end(), BookingStatus.CONFIRMED,
                "America/Bogota", null));
            bookingResources.saveAndFlush(BookingResource.create(second, false));
        }));
        assertEquals(1, bookings.count());
    }

    @Test
    void pendingBookingsCanBeApprovedOrRejectedOnlyByStaff() throws Exception {
        Fixture fixture = fixture(1, true);
        String body = body(fixture);
        String pending = mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", "pending-one").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        UUID firstId = UUID.fromString(json.readTree(pending).get("id").asText());

        mvc.perform(post("/api/v1/bookings/{id}/approve", firstId).header("Authorization", bearer(fixture.token())))
            .andExpect(status().isForbidden());

        User staff = users.save(User.create(UUID.randomUUID() + "@example.test", "unused", "Booking administrator"));
        memberships.save(CompanyMembership.create(fixture.companyId(), staff.getId(), Set.of(MembershipRole.COMPANY_ADMIN)));
        String staffToken = jwt.generateToken(staff.getId(), fixture.companyId(), List.of("COMPANY_ADMIN"));
        mvc.perform(post("/api/v1/bookings/{id}/reject", firstId).header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        String second = mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", "pending-two").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        UUID secondId = UUID.fromString(json.readTree(second).get("id").asText());
        mvc.perform(post("/api/v1/bookings/{id}/approve", secondId).header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/v1/bookings/{id}/reject", secondId).header("Authorization", bearer(staffToken)))
            .andExpect(status().isUnprocessableContent());
    }

    private int createConcurrently(Fixture fixture, String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mvc.perform(post("/api/v1/bookings").header("Authorization", bearer(fixture.token()))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body(fixture)))
            .andReturn().getResponse().getStatus();
    }

    private Fixture fixture(int capacity) {
        return fixture(capacity, false);
    }

    private Fixture fixture(int capacity, boolean approvalRequired) {
        Company company = Company.create("Booking company", "Booking company", "booking-" + UUID.randomUUID(), "booking@example.test");
        company.activate();
        company = companies.save(company);
        User user = users.save(User.create(UUID.randomUUID() + "@example.test", "unused", "Booking customer"));
        memberships.save(CompanyMembership.create(company.getId(), user.getId(), Set.of(MembershipRole.CUSTOMER)));
        Resource resource = Resource.create(company.getId(), "Court " + UUID.randomUUID(), null,
            ResourceType.SPACE, capacity, ResourceVisibility.MEMBERS);
        resource.activate();
        resource = resources.save(resource);
        BookingPolicy policy = policies.save(BookingPolicy.create(company.getId(), "Standard", 30, 60, 30, 0, 30,
            0, approvalRequired, true));
        assignments.save(ResourcePolicy.create(resource.getId(), policy.getId(), Instant.now().minusSeconds(60), null));
        LocalDate date = LocalDate.now().plusDays(2);
        rules.save(AvailabilityRule.create(company.getId(), resource.getId(),
            (short) (date.getDayOfWeek().getValue() % 7), LocalTime.of(9, 0), LocalTime.of(10, 0), null, null));
        Instant start = date.atTime(9, 0).atZone(ZoneId.of(company.getTimezone())).toInstant();
        return new Fixture(jwt.generateToken(user.getId(), company.getId(), List.of("CUSTOMER")), company.getId(),
            user.getId(), resource.getId(), start, start.plusSeconds(1800));
    }

    private String body(Fixture fixture) {
        return "{\"resourceId\":\"" + fixture.resourceId() + "\",\"startAt\":\"" + fixture.start()
            + "\",\"endAt\":\"" + fixture.end() + "\",\"notes\":\"Original\"}";
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record Fixture(String token, UUID companyId, UUID userId, UUID resourceId, Instant start, Instant end) {}
}
