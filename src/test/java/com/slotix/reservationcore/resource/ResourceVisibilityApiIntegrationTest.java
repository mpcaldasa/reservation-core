package com.slotix.reservationcore.resource;

import com.slotix.reservationcore.availability.AvailabilityRule;
import com.slotix.reservationcore.availability.AvailabilityRuleRepository;
import com.slotix.reservationcore.identity.CompanyMembership;
import com.slotix.reservationcore.identity.IdentityIntegrationTestSupport;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.policy.BookingPolicy;
import com.slotix.reservationcore.policy.BookingPolicyRepository;
import com.slotix.reservationcore.policy.ResourcePolicy;
import com.slotix.reservationcore.policy.ResourcePolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceVisibilityApiIntegrationTest extends IdentityIntegrationTestSupport {
    @Autowired private ResourceRepository resources;
    @Autowired private AvailabilityRuleRepository rules;
    @Autowired private BookingPolicyRepository policies;
    @Autowired private ResourcePolicyRepository assignments;

    @Test
    void catalogFiltersPrivateResourcesAndRequiresAuthenticationForPublicResources() throws Exception {
        Fixture fixture = fixture();
        resource(fixture, ResourceVisibility.PUBLIC);
        resource(fixture, ResourceVisibility.MEMBERS);
        resource(fixture, ResourceVisibility.PRIVATE);
        String path = catalog(fixture);
        mvc.perform(get(path).header("Authorization", fixture.customerToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(path).header("Authorization", fixture.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get(path).header("Authorization", managerToken(fixture)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void privateResourceDetailsAreHiddenFromCustomersAndReadableByStaff() throws Exception {
        Fixture fixture = fixture();
        Resource resource = resource(fixture, ResourceVisibility.PRIVATE);
        String manager = managerToken(fixture);
        for (String suffix : new String[]{"availability-rules", "blocks", "policies", "availability"}) {
            String path = catalog(fixture) + "/" + resource.getId() + "/" + suffix;
            mvc.perform(get(path).header("Authorization", fixture.customerToken())
                    .param("date", LocalDate.now().plusDays(2).toString()).param("durationMinutes", "30"))
                .andExpect(status().isNotFound());
            for (String staff : new String[]{fixture.adminToken(), manager}) {
                mvc.perform(get(path).header("Authorization", staff)
                        .param("date", LocalDate.now().plusDays(2).toString()).param("durationMinutes", "30"))
                    .andExpect(status().isOk());
            }
        }
    }

    @Test
    void deniedPrivateBookingDoesNotPersistSideEffects() throws Exception {
        Fixture fixture = fixture();
        Resource resource = resource(fixture, ResourceVisibility.PRIVATE);
        String body = bookingBody(fixture, resource);
        mvc.perform(post("/api/v1/bookings")
                .header("Authorization", fixture.customerToken()).header("Idempotency-Key", "private-denied")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
        for (String table : new String[]{"bookings", "booking_resources", "idempotency_keys", "audit_logs", "outbox_events"}) {
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class));
        }
    }

    @Test
    void staffCanBookPrivateResourcesForCustomersAndCustomersRetainBookingHistory() throws Exception {
        Fixture fixture = fixture();
        for (String staff : new String[]{fixture.adminToken(), managerToken(fixture)}) {
            Resource resource = resource(fixture, ResourceVisibility.PRIVATE);
            String response = mvc.perform(post("/api/v1/bookings")
                    .header("Authorization", staff).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(bookingBody(fixture, resource)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String bookingId = json.readTree(response).get("id").asText();
            mvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", fixture.customerToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resourceId").value(resource.getId().toString()));
        }
    }

    @Test
    void customersCanBookPublicAndMemberResources() throws Exception {
        Fixture fixture = fixture();
        for (ResourceVisibility visibility : new ResourceVisibility[]{ResourceVisibility.PUBLIC, ResourceVisibility.MEMBERS}) {
            Resource resource = resource(fixture, visibility);
            mvc.perform(post("/api/v1/bookings")
                    .header("Authorization", fixture.customerToken()).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(bookingBody(fixture, resource)))
                .andExpect(status().isCreated());
        }
    }

    @Test
    void roleRevocationRemovesPrivateAccessFromExistingToken() throws Exception {
        Fixture fixture = fixture();
        User manager = user("manager");
        CompanyMembership membership = memberships.save(CompanyMembership.create(fixture.company().getId(), manager.getId(),
            Set.of(MembershipRole.BOOKING_MANAGER)));
        String token = token(manager, fixture.company(), MembershipRole.BOOKING_MANAGER);
        Resource resource = resource(fixture, ResourceVisibility.PRIVATE);
        mvc.perform(put("/api/v1/companies/" + fixture.company().getId() + "/memberships/" + membership.getId() + "/roles")
                .header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"CUSTOMER\"]}"))
            .andExpect(status().isOk());
        mvc.perform(get(catalog(fixture)).header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(catalog(fixture) + "/" + resource.getId() + "/availability-rules").header("Authorization", token))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings")
                .header("Authorization", token).header("Idempotency-Key", "revoked-private")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                    "resourceId", resource.getId(), "startAt", Instant.now().plusSeconds(86400).toString(),
                    "endAt", Instant.now().plusSeconds(88200).toString()))))
            .andExpect(status().isNotFound());
    }

    @Test
    void staffCannotReadResourcesBelongingToAnotherCompany() throws Exception {
        Fixture own = fixture();
        Fixture other = fixture();
        Resource resource = resource(other, ResourceVisibility.PRIVATE);
        mvc.perform(get(catalog(own) + "/" + resource.getId() + "/blocks").header("Authorization", own.adminToken()))
            .andExpect(status().isNotFound());
        mvc.perform(get(catalog(other) + "/" + resource.getId() + "/blocks").header("Authorization", own.adminToken()))
            .andExpect(status().isForbidden());
    }

    private Resource resource(Fixture fixture, ResourceVisibility visibility) {
        Resource resource = Resource.create(fixture.company().getId(), visibility + " " + UUID.randomUUID(), null,
            ResourceType.SPACE, 1, visibility);
        resource.activate();
        return resources.save(resource);
    }

    private String managerToken(Fixture fixture) {
        User manager = user("manager");
        memberships.save(CompanyMembership.create(fixture.company().getId(), manager.getId(), Set.of(MembershipRole.BOOKING_MANAGER)));
        return token(manager, fixture.company(), MembershipRole.BOOKING_MANAGER);
    }

    private String catalog(Fixture fixture) {
        return "/api/v1/companies/" + fixture.company().getId() + "/resources";
    }

    private String bookingBody(Fixture fixture, Resource resource) throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of(fixture.company().getTimezone())).plusDays(2);
        Instant start = date.atTime(9, 0).atZone(ZoneId.of(fixture.company().getTimezone())).toInstant();
        rules.save(AvailabilityRule.create(fixture.company().getId(), resource.getId(),
            (short) (date.getDayOfWeek().getValue() % 7), LocalTime.of(9, 0), LocalTime.of(10, 0), null, null));
        BookingPolicy policy = policies.save(BookingPolicy.create(fixture.company().getId(), "Policy " + resource.getId(), 30, 60,
            30, 0, 30, 0, false, true));
        assignments.save(ResourcePolicy.create(resource.getId(), policy.getId(), Instant.now().minusSeconds(60), null));
        return json.writeValueAsString(Map.of("resourceId", resource.getId(), "startAt", start.toString(),
            "endAt", start.plusSeconds(1800).toString(), "customerUserId", fixture.customer().getId()));
    }
}
