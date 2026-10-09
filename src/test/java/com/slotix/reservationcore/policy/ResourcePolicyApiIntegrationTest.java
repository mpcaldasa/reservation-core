package com.slotix.reservationcore.policy;

import com.slotix.reservationcore.PostgresIntegrationTestSupport;
import com.slotix.reservationcore.availability.AvailabilityRule;
import com.slotix.reservationcore.availability.AvailabilityRuleRepository;
import com.slotix.reservationcore.availability.ResourceBlockRepository;
import com.slotix.reservationcore.booking.BookingRepository;
import com.slotix.reservationcore.booking.BookingResourceRepository;
import com.slotix.reservationcore.booking.Booking;
import com.slotix.reservationcore.booking.BookingResource;
import com.slotix.reservationcore.booking.BookingStatus;
import com.slotix.reservationcore.booking.IdempotencyKeyRepository;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.common.JwtService;
import com.slotix.reservationcore.identity.CompanyMembership;
import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.identity.UserRepository;
import com.slotix.reservationcore.resource.Resource;
import com.slotix.reservationcore.resource.ResourceRepository;
import com.slotix.reservationcore.resource.ResourceType;
import com.slotix.reservationcore.resource.ResourceVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourcePolicyApiIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired private MockMvc mvc;
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
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from identity_email_deliveries");
        jdbc.update("delete from company_invitations");
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
    void adminAssignsAdjacentPeriodsAndRejectsOverlapAndInvalidRange() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy policy = policy(company, 30, 120, 15, 0, 30);
        String token = token(company, MembershipRole.COMPANY_ADMIN);
        String path = path(company, resource);
        Instant from = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS);
        Instant boundary = from.plusSeconds(86_400);

        mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(policy, from, boundary)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.policyId").value(policy.getId().toString()));
        mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(policy, boundary, null)))
            .andExpect(status().isCreated());
        mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(policy, from.plusSeconds(60), boundary.plusSeconds(60))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("POLICY_ASSIGNMENT_OVERLAP"));
        mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(policy, boundary, from)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INVALID_POLICY_ASSIGNMENT_INTERVAL"));
        mvc.perform(get(path).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void onlyOwnCompanyAdminCanAssignAnOwnCompanyPolicy() throws Exception {
        Company own = company();
        Company other = company();
        Resource resource = resource(own);
        BookingPolicy ownPolicy = policy(own, 30, 120, 15, 0, 30);
        BookingPolicy otherPolicy = policy(other, 30, 120, 15, 0, 30);
        String path = path(own, resource);
        Instant from = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS);

        mvc.perform(post(path).header("Authorization", bearer(token(own, MembershipRole.CUSTOMER)))
                .contentType(MediaType.APPLICATION_JSON).content(body(ownPolicy, from, null)))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(token(other, MembershipRole.COMPANY_ADMIN)))
                .contentType(MediaType.APPLICATION_JSON).content(body(ownPolicy, from, null)))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(token(own, MembershipRole.COMPANY_ADMIN)))
                .contentType(MediaType.APPLICATION_JSON).content(body(otherPolicy, from, null)))
            .andExpect(status().isNotFound());
    }

    @Test
    void availabilityRespectsDurationNoticeAdvanceAndPolicyEnd() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy policy = policy(company, 30, 60, 15, 60, 3);
        LocalDate date = LocalDate.now().plusDays(2);
        rules.save(AvailabilityRule.create(company.getId(), resource.getId(),
            (short) (date.getDayOfWeek().getValue() % 7), LocalTime.of(9, 0), LocalTime.of(11, 0), null, null));
        Instant from = date.atTime(9, 0).atZone(java.time.ZoneId.of(company.getTimezone())).toInstant();
        assignments.save(ResourcePolicy.create(resource.getId(), policy.getId(), from, from.plusSeconds(3600)));
        String endpoint = "/api/v1/companies/" + company.getId() + "/resources/" + resource.getId() + "/availability";
        String token = token(company, MembershipRole.CUSTOMER);

        mvc.perform(get(endpoint).header("Authorization", bearer(token)).param("date", date.toString()).param("durationMinutes", "30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get(endpoint).header("Authorization", bearer(token)).param("date", date.toString()).param("durationMinutes", "40"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(endpoint).header("Authorization", bearer(token)).param("date", date.toString()).param("durationMinutes", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AVAILABILITY_DURATION_INVALID"));
        mvc.perform(get(endpoint).header("Authorization", bearer(token)).param("date", date.plusDays(7).toString()).param("durationMinutes", "30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void availabilityAppliesMinimumNoticeAndMaximumAdvance() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy policy = policy(company, 30, 60, 15, 2880, 4);
        assignments.save(ResourcePolicy.create(resource.getId(), policy.getId(), Instant.now().minusSeconds(60), null));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate withinWindow = LocalDate.now().plusDays(3);
        LocalDate beyondWindow = LocalDate.now().plusDays(7);
        for (LocalDate date : List.of(tomorrow, withinWindow, beyondWindow)) {
            rules.save(AvailabilityRule.create(company.getId(), resource.getId(),
                (short) (date.getDayOfWeek().getValue() % 7), LocalTime.of(9, 0), LocalTime.of(10, 0), null, null));
        }
        String endpoint = "/api/v1/companies/" + company.getId() + "/resources/" + resource.getId() + "/availability";
        String token = token(company, MembershipRole.CUSTOMER);

        mvc.perform(get(endpoint).header("Authorization", bearer(token)).param("date", tomorrow.toString()).param("durationMinutes", "30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(endpoint).header("Authorization", bearer(token)).param("date", withinWindow.toString()).param("durationMinutes", "30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get(endpoint).header("Authorization", bearer(token)).param("date", beyondWindow.toString()).param("durationMinutes", "30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminCanReplacePolicyWhenAssignmentHasNoActiveReservations() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy original = policy(company, 30, 120, 15, 0, 30);
        BookingPolicy replacement = policy(company, 15, 90, 15, 0, 30);
        Instant from = Instant.now().plusSeconds(86_400);
        Instant to = from.plusSeconds(86_400);
        assignments.saveAndFlush(ResourcePolicy.create(resource.getId(), original.getId(), from, to));
        String token = token(company, MembershipRole.COMPANY_ADMIN);
        String endpoint = path(company, resource) + "/" + from + "/policy";

        mvc.perform(put(endpoint).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyId\":\"" + replacement.getId() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyId").value(replacement.getId().toString()))
            .andExpect(jsonPath("$.effectiveFrom").value(from.toString()))
            .andExpect(jsonPath("$.effectiveTo").value(to.toString()));

        mvc.perform(put(endpoint).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyId\":\"" + replacement.getId() + "\"}"))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(1L, jdbc.queryForObject(
            "select count(*) from audit_logs where action = 'RESOURCE_POLICY_ASSIGNMENT_POLICY_REPLACED'", Long.class));
    }

    @Test
    void activeReservationsPreventPolicyReplacementWithStableErrorCode() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy original = policy(company, 30, 120, 15, 0, 30);
        BookingPolicy replacement = policy(company, 15, 90, 15, 0, 30);
        Instant from = Instant.now().plusSeconds(86_400);
        Instant start = from.plusSeconds(3600);
        Instant end = start.plusSeconds(1800);
        assignments.saveAndFlush(ResourcePolicy.create(resource.getId(), original.getId(), from, null));
        User customer = users.save(User.create(UUID.randomUUID() + "@example.test", "unused", "Test customer"));
        Booking booking = bookings.saveAndFlush(Booking.create(company.getId(), customer.getId(), resource.getId(),
            start, end, BookingStatus.CONFIRMED, company.getTimezone(), null));
        bookingResources.saveAndFlush(BookingResource.create(booking, false));
        String token = token(company, MembershipRole.COMPANY_ADMIN);
        String endpoint = path(company, resource) + "/" + from + "/policy";

        mvc.perform(put(endpoint).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyId\":\"" + replacement.getId() + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ACTIVE_RESERVATIONS_PREVENT_ASSIGNMENT_CHANGE"))
            .andExpect(jsonPath("$.error").value(
                "The policy cannot be replaced because active reservations use this assignment"));
    }

    @Test
    void assignmentCanOnlyBeShortenedAndRepeatingTheSameClosureHasNoExtraAudit() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy policy = policy(company, 30, 120, 15, 0, 30);
        Instant from = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS);
        Instant originalTo = from.plusSeconds(86_400);
        Instant shortenedTo = from.plusSeconds(43_200);
        assignments.saveAndFlush(ResourcePolicy.create(resource.getId(), policy.getId(), from, null));
        String token = token(company, MembershipRole.COMPANY_ADMIN);
        String endpoint = path(company, resource) + "/" + from;
        String closeBody = "{\"effectiveTo\":\"" + shortenedTo + "\"}";

        mvc.perform(put(endpoint).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(closeBody))
            .andExpect(status().isOk()).andExpect(jsonPath("$.effectiveTo").value(shortenedTo.toString()));
        mvc.perform(put(endpoint).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(closeBody))
            .andExpect(status().isOk());
        mvc.perform(put(endpoint).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveTo\":\"" + originalTo + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("POLICY_ASSIGNMENT_CANNOT_BE_EXTENDED"));

        assertEquals(1L, jdbc.queryForObject(
            "select count(*) from audit_logs where action = 'RESOURCE_POLICY_ASSIGNMENT_CLOSED'", Long.class));
    }

    @Test
    void activeReservationPreventsClosingAssignmentBeforeItsEnd() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy policy = policy(company, 30, 120, 15, 0, 30);
        Instant from = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS);
        Instant bookingStart = from.plusSeconds(7200);
        assignments.saveAndFlush(ResourcePolicy.create(resource.getId(), policy.getId(), from, null));
        User customer = users.save(User.create(UUID.randomUUID() + "@example.test", "unused", "Test customer"));
        Booking booking = bookings.saveAndFlush(Booking.create(company.getId(), customer.getId(), resource.getId(),
            bookingStart, bookingStart.plusSeconds(1800), BookingStatus.CONFIRMED, company.getTimezone(), null));
        bookingResources.saveAndFlush(BookingResource.create(booking, false));
        String token = token(company, MembershipRole.COMPANY_ADMIN);

        mvc.perform(put(path(company, resource) + "/" + from).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveTo\":\"" + from.plusSeconds(3600) + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ACTIVE_RESERVATIONS_PREVENT_ASSIGNMENT_CHANGE"));
    }

    @Test
    void onlyFutureAssignmentsWithoutReservationsCanBeRemoved() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy policy = policy(company, 30, 120, 15, 0, 30);
        Instant futureFrom = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS);
        Instant currentFrom = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        assignments.saveAndFlush(ResourcePolicy.create(resource.getId(), policy.getId(), futureFrom, null));
        assignments.saveAndFlush(ResourcePolicy.create(resource.getId(), policy.getId(), currentFrom, futureFrom));
        String token = token(company, MembershipRole.COMPANY_ADMIN);
        String base = path(company, resource) + "/";

        mvc.perform(delete(base + futureFrom).header("Authorization", bearer(token)))
            .andExpect(status().isNoContent());
        mvc.perform(delete(base + currentFrom).header("Authorization", bearer(token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("POLICY_ASSIGNMENT_NOT_FUTURE"));

        assertEquals(1L, jdbc.queryForObject("select count(*) from resource_policies where resource_id = ?",
            Long.class, resource.getId()));
        assertEquals(1L, jdbc.queryForObject(
            "select count(*) from audit_logs where action = 'RESOURCE_POLICY_ASSIGNMENT_REMOVED'", Long.class));
    }

    @Test
    void concurrentOverlappingAssignmentsSerializeOnCompanyLock() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy firstPolicy = policy(company, 30, 120, 15, 0, 30);
        BookingPolicy secondPolicy = policy(company, 30, 120, 15, 0, 30);
        String token = token(company, MembershipRole.COMPANY_ADMIN);
        String endpoint = path(company, resource);
        Instant from = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS);
        String firstBody = body(firstPolicy, from, null);
        String secondBody = body(secondPolicy, from, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> assignAfterStart(endpoint, token, firstBody, ready, start));
            var second = executor.submit(() -> assignAfterStart(endpoint, token, secondBody, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(Set.of(201, 409), Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
        }
        assertEquals(1, assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(resource.getId()).size());
    }

    @Test
    void unassignedPolicyCanBeEditedAndLifecycleNoOpsDoNotDuplicateAudit() throws Exception {
        Company company = company();
        BookingPolicy policy = policy(company, 30, 120, 15, 0, 30);
        String token = token(company, MembershipRole.COMPANY_ADMIN);
        String endpoint = "/api/v1/companies/" + company.getId() + "/booking-policies/" + policy.getId();
        String update = """
            {"name":"Updated policy","minDurationMinutes":30,"maxDurationMinutes":120,
             "slotIncrementMinutes":15,"minNoticeMinutes":0,"maxAdvanceDays":30,
             "cancellationNoticeMinutes":0,"approvalRequired":false,"allowCustomerCancel":true}
            """;

        mvc.perform(put(endpoint).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(update))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated policy"));
        mvc.perform(post(endpoint + "/deactivate").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(post(endpoint + "/deactivate").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(post(endpoint + "/activate").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post(endpoint + "/activate").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        assertEquals(1L, jdbc.queryForObject("select count(*) from audit_logs where action = 'BOOKING_POLICY_UPDATED'", Long.class));
        assertEquals(1L, jdbc.queryForObject("select count(*) from audit_logs where action = 'BOOKING_POLICY_DEACTIVATED'", Long.class));
        assertEquals(1L, jdbc.queryForObject("select count(*) from audit_logs where action = 'BOOKING_POLICY_ACTIVATED'", Long.class));
    }

    @Test
    void assignedPolicyCannotBeDeactivatedAndCustomersCannotManagePolicyLifecycle() throws Exception {
        Company company = company();
        Resource resource = resource(company);
        BookingPolicy policy = policy(company, 30, 120, 15, 0, 30);
        Instant from = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        assignments.saveAndFlush(ResourcePolicy.create(resource.getId(), policy.getId(), from, null));
        String endpoint = "/api/v1/companies/" + company.getId() + "/booking-policies/" + policy.getId();

        mvc.perform(post(endpoint + "/deactivate")
                .header("Authorization", bearer(token(company, MembershipRole.CUSTOMER))))
            .andExpect(status().isForbidden());
        mvc.perform(post(endpoint + "/deactivate")
                .header("Authorization", bearer(token(company, MembershipRole.COMPANY_ADMIN))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BOOKING_POLICY_HAS_ACTIVE_ASSIGNMENTS"));
        assertEquals("ACTIVE", policies.findById(policy.getId()).orElseThrow().getStatus());
    }

    private int assignAfterStart(String endpoint, String token, String request, CountDownLatch ready,
                                 CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return mvc.perform(post(endpoint).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(request))
            .andReturn().getResponse().getStatus();
    }

    private Company company() {
        Company c = Company.create("Test company", "Test company", "company-" + UUID.randomUUID(), "test@example.test");
        c.activate();
        return companies.save(c);
    }

    private Resource resource(Company c) {
        Resource resource = Resource.create(c.getId(), "Room " + UUID.randomUUID(), null,
            ResourceType.SPACE, 1, ResourceVisibility.MEMBERS);
        resource.activate();
        return resources.save(resource);
    }

    private BookingPolicy policy(Company c, int min, int max, int increment, int notice, int advance) {
        return policies.save(BookingPolicy.create(c.getId(), "Policy " + UUID.randomUUID(), min, max,
            increment, notice, advance, 0, false, true));
    }

    private String token(Company c, MembershipRole role) {
        User user = users.save(User.create(UUID.randomUUID() + "@example.test", "unused", "Test user"));
        memberships.save(CompanyMembership.create(c.getId(), user.getId(), Set.of(role)));
        return jwt.generateToken(user.getId(), c.getId(), List.of(role.name()));
    }

    private String path(Company c, Resource r) {
        return "/api/v1/companies/" + c.getId() + "/resources/" + r.getId() + "/policies";
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String body(BookingPolicy p, Instant from, Instant to) {
        return "{\"policyId\":\"" + p.getId() + "\",\"effectiveFrom\":\"" + from + "\",\"effectiveTo\":"
            + (to == null ? "null" : "\"" + to + "\"") + "}";
    }
}
