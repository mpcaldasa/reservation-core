package com.slotix.reservationcore.policy;

import com.slotix.reservationcore.PostgresIntegrationTestSupport;
import com.slotix.reservationcore.availability.AvailabilityRule;
import com.slotix.reservationcore.availability.AvailabilityRuleRepository;
import com.slotix.reservationcore.availability.ResourceBlockRepository;
import com.slotix.reservationcore.booking.BookingRepository;
import com.slotix.reservationcore.booking.BookingResourceRepository;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        Instant from = Instant.now().plusSeconds(86_400);
        Instant boundary = from.plusSeconds(86_400);

        mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(policy, from, boundary)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.policyId").value(policy.getId().toString()));
        mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(policy, boundary, null)))
            .andExpect(status().isCreated());
        mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(policy, from.plusSeconds(60), boundary.plusSeconds(60))))
            .andExpect(status().isConflict());
        mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(policy, boundary, from)))
            .andExpect(status().isUnprocessableContent());
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
        Instant from = Instant.now().plusSeconds(86_400);

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
