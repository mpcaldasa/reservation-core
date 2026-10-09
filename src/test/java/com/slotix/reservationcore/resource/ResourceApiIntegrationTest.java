package com.slotix.reservationcore.resource;

import com.slotix.reservationcore.PostgresIntegrationTestSupport;
import com.slotix.reservationcore.common.JwtService;
import com.slotix.reservationcore.availability.AvailabilityRuleRepository;
import com.slotix.reservationcore.availability.ResourceBlockRepository;
import com.slotix.reservationcore.policy.BookingPolicyRepository;
import com.slotix.reservationcore.policy.ResourcePolicyRepository;
import com.slotix.reservationcore.booking.BookingRepository;
import com.slotix.reservationcore.booking.BookingResourceRepository;
import com.slotix.reservationcore.booking.Booking;
import com.slotix.reservationcore.booking.BookingResource;
import com.slotix.reservationcore.booking.BookingStatus;
import com.slotix.reservationcore.booking.IdempotencyKeyRepository;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.CompanyMembership;
import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceApiIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyMembershipRepository companyMembershipRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private AvailabilityRuleRepository availabilityRuleRepository;
    @Autowired private ResourceBlockRepository resourceBlockRepository;
    @Autowired private ResourcePolicyRepository resourcePolicyRepository;
    @Autowired private BookingPolicyRepository bookingPolicyRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingResourceRepository bookingResourceRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from identity_email_deliveries");
        jdbc.update("delete from company_invitations");
        jdbc.update("delete from notification_deliveries");
        jdbc.update("delete from outbox_events");
        jdbc.update("delete from audit_logs");
        idempotencyKeyRepository.deleteAll();
        bookingResourceRepository.deleteAll();
        bookingRepository.deleteAll();
        resourceBlockRepository.deleteAll();
        availabilityRuleRepository.deleteAll();
        resourcePolicyRepository.deleteAll();
        bookingPolicyRepository.deleteAll();
        resourceRepository.deleteAll();
        companyMembershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void companyAdminCanConfigureAvailabilityAndBlocksForOwnResource() throws Exception {
        Company company = activeCompany("availability-company");
        Resource resource = resourceRepository.save(Resource.create(company.getId(), "Studio", null, ResourceType.SPACE, 1, ResourceVisibility.MEMBERS));
        String token = companyAdminToken(company);

        mockMvc.perform(post("/api/v1/companies/{companyId}/resources/{resourceId}/availability-rules", company.getId(), resource.getId())
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"weekday\":1,\"startLocalTime\":\"09:00:00\",\"endLocalTime\":\"17:00:00\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.weekday").value(1));

        mockMvc.perform(post("/api/v1/companies/{companyId}/resources/{resourceId}/blocks", company.getId(), resource.getId())
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startAt\":\"2026-10-05T12:00:00Z\",\"endAt\":\"2026-10-05T13:00:00Z\",\"reason\":\"Maintenance\",\"blockType\":\"MAINTENANCE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.blockType").value("MAINTENANCE"));
    }

    @Test
    void companyAdminCanCreateAndListResourcesInTheirOwnCompany() throws Exception {
        Company company = activeCompany("main-office");
        String token = companyAdminToken(company);

        mockMvc.perform(post("/api/v1/companies/{companyId}/resources", company.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Boardroom A","description":"Seats up to twelve people","resourceType":"SPACE","capacity":12,"visibility":"MEMBERS"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.companyId").value(company.getId().toString()))
            .andExpect(jsonPath("$.name").value("Boardroom A"))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/companies/{companyId}/resources", company.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Boardroom A"));
    }

    @Test
    void companyAdminCannotReadResourcesFromAnotherCompany() throws Exception {
        Company authorizedCompany = activeCompany("authorized-company");
        Company otherCompany = activeCompany("other-company");
        String token = companyAdminToken(authorizedCompany);

        mockMvc.perform(get("/api/v1/companies/{companyId}/resources", otherCompany.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void resourceDeletionIsIdempotentAndHidesTheResource() throws Exception {
        Company company = activeCompany("resource-deletion");
        Resource resource = resourceRepository.save(Resource.create(company.getId(), "Studio", null,
            ResourceType.SPACE, 1, ResourceVisibility.MEMBERS));
        String token = companyAdminToken(company);

        mockMvc.perform(delete("/api/v1/companies/{companyId}/resources/{resourceId}", company.getId(), resource.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/companies/{companyId}/resources/{resourceId}", company.getId(), resource.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/companies/{companyId}/resources", company.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        Resource deleted = resourceRepository.findById(resource.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
        assertEquals(1L, jdbc.queryForObject(
            "select count(*) from audit_logs where action = 'RESOURCE_DELETED'", Long.class));
    }

    @Test
    void activeReservationsPreventResourceDeletionWithStableErrorCode() throws Exception {
        Company company = activeCompany("resource-deletion-conflict");
        Resource resource = resourceRepository.save(Resource.create(company.getId(), "Studio", null,
            ResourceType.SPACE, 1, ResourceVisibility.MEMBERS));
        User customer = userRepository.save(User.create(UUID.randomUUID() + "@example.test", "unused", "Customer"));
        Instant start = Instant.now().plusSeconds(3600);
        Booking booking = bookingRepository.saveAndFlush(Booking.create(company.getId(), customer.getId(),
            resource.getId(), start, start.plusSeconds(1800), BookingStatus.CONFIRMED, company.getTimezone(), null));
        bookingResourceRepository.saveAndFlush(BookingResource.create(booking, false));
        String token = companyAdminToken(company);

        mockMvc.perform(delete("/api/v1/companies/{companyId}/resources/{resourceId}", company.getId(), resource.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ACTIVE_RESERVATIONS_PREVENT_RESOURCE_DELETION"))
            .andExpect(jsonPath("$.error").value("The resource cannot be deleted while active reservations remain"));
    }

    private Company activeCompany(String slug) {
        Company company = Company.create(slug + " Legal Name", slug + " Display Name", slug + "-" + UUID.randomUUID(), slug + "@example.test");
        company.activate();
        return companyRepository.save(company);
    }

    private String companyAdminToken(Company company) {
        User user = userRepository.save(User.create(UUID.randomUUID() + "@example.test", "unused-password-hash", "Company Administrator"));
        companyMembershipRepository.save(CompanyMembership.create(company.getId(), user.getId(), Set.of(MembershipRole.COMPANY_ADMIN)));
        return jwtService.generateToken(user.getId(), company.getId(), List.of("COMPANY_ADMIN"));
    }
}
