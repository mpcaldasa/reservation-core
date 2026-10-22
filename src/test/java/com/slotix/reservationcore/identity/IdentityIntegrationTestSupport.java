package com.slotix.reservationcore.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotix.reservationcore.PostgresIntegrationTestSupport;
import com.slotix.reservationcore.common.JwtService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IdentityIntegrationTestSupport extends PostgresIntegrationTestSupport {
    protected static final String PASSWORD = "correct-horse-42";
    protected final ObjectMapper json = new ObjectMapper();
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected CompanyRepository companies;
    @Autowired protected CompanyMembershipRepository memberships;
    @Autowired protected PlatformUserRoleRepository platformRoles;
    @Autowired protected PlatformBootstrapService bootstrap;
    @Autowired protected JwtService jwt;
    @Autowired protected PasswordEncoder passwords;

    @BeforeEach
    void cleanDatabase() {
        for (String table : List.of("identity_rate_limits", "revoked_access_tokens", "identity_email_deliveries", "company_invitations", "password_reset_tokens",
            "notification_deliveries", "outbox_events", "audit_logs", "idempotency_keys",
            "booking_resources", "bookings", "resource_blocks", "availability_rules", "resource_policies",
            "booking_policies", "resources", "membership_roles", "company_memberships", "platform_user_roles", "users", "companies")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @AfterEach
    void removeTestData() {
        cleanDatabase();
    }

    protected String platformToken() throws Exception {
        bootstrap.initialize(new PlatformBootstrapProperties("platform@example.test", PASSWORD, "Platform Administrator"));
        String body = mvc.perform(post("/api/platform/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "platform@example.test", "password", PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    protected Fixture fixture() {
        Company company = Company.create("Membership Company", "Membership Company", "member-" + UUID.randomUUID(), "contact@example.test");
        company.activate();
        company = companies.save(company);
        User admin = user("admin");
        User customer = user("customer");
        CompanyMembership adminMembership = memberships.save(CompanyMembership.create(company.getId(), admin.getId(), Set.of(MembershipRole.COMPANY_ADMIN)));
        CompanyMembership customerMembership = memberships.save(CompanyMembership.create(company.getId(), customer.getId(), Set.of(MembershipRole.CUSTOMER)));
        return new Fixture(company, admin, customer, adminMembership, customerMembership,
            token(admin, company, MembershipRole.COMPANY_ADMIN), token(customer, company, MembershipRole.CUSTOMER));
    }

    protected User user(String label) {
        return users.save(User.create(label + "-" + UUID.randomUUID() + "@example.test", passwords.encode(PASSWORD), label));
    }

    protected String token(User user, Company company, MembershipRole role) {
        return "Bearer " + jwt.generateToken(user.getId(), company.getId(), List.of(role.name()));
    }

    protected String login(String email, UUID companyId) throws Exception {
        String body = mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "companyId", companyId))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    protected record Fixture(Company company, User admin, User customer, CompanyMembership adminMembership,
                             CompanyMembership customerMembership, String adminToken, String customerToken) {}
}
