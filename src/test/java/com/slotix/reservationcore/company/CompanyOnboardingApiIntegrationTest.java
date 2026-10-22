package com.slotix.reservationcore.company;

import com.slotix.reservationcore.identity.IdentityIntegrationTestSupport;
import com.slotix.reservationcore.identity.PlatformBootstrapProperties;
import com.slotix.reservationcore.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyOnboardingApiIntegrationTest extends IdentityIntegrationTestSupport {
    @Test
    void bootstrapCreatesOnePlatformAdministratorAndDoesNotResetCredentials() throws Exception {
        var properties = new PlatformBootstrapProperties("Platform@EXAMPLE.test", PASSWORD, "Platform Administrator");
        UUID id = bootstrap.initialize(properties).orElseThrow();
        String originalHash = users.findById(id).orElseThrow().getPasswordHash();
        assertTrue(bootstrap.initialize(new PlatformBootstrapProperties(properties.email(), "different-password", properties.fullName())).isEmpty());
        assertEquals(1, platformRoles.count());
        assertEquals(originalHash, users.findById(id).orElseThrow().getPasswordHash());
        assertTrue(passwords.matches(PASSWORD, originalHash));
        assertEquals(0, memberships.count());
        String token = platformToken();
        assertNull(jwt.parseAndValidate(token.substring(7)).get("companyId"));
        mvc.perform(get("/api/companies").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void bootstrapRejectsInvalidConfigurationWithoutCreatingAccounts() {
        assertThrows(IllegalStateException.class, () -> bootstrap.initialize(
            new PlatformBootstrapProperties("invalid", "short", "Administrator")));
        assertEquals(0, users.count());
        assertEquals(0, platformRoles.count());
    }

    @Test
    void concurrentBootstrapCreatesOnlyOneAdministrator() throws Exception {
        var properties = new PlatformBootstrapProperties("platform@example.test", PASSWORD, "Administrator");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return bootstrap.initialize(properties);
            });
            var second = executor.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return bootstrap.initialize(properties);
            });
            start.countDown();
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(result -> result.isPresent()).count());
        }
        assertEquals(1, platformRoles.count());
        assertEquals(1, users.count());
    }

    @Test
    void platformAdministratorCanOnboardANewCompanyAndItsFirstAdministrator() throws Exception {
        String root = platformToken();
        String body = mvc.perform(post("/api/v1/companies/onboarding").header("Authorization", root)
                .contentType(MediaType.APPLICATION_JSON).content(onboarding("new-company", Map.of("newUser", Map.of(
                    "email", "Administrator@EXAMPLE.test", "password", PASSWORD, "fullName", "Company Administrator")))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.company.status").value("ACTIVE"))
            .andExpect(jsonPath("$.administrator.status").value("ACTIVE"))
            .andExpect(jsonPath("$.administrator.roles[0]").value("COMPANY_ADMIN"))
            .andReturn().getResponse().getContentAsString();
        var response = json.readTree(body);
        UUID companyId = UUID.fromString(response.get("company").get("id").asText());
        UUID administratorId = UUID.fromString(response.get("administrator").get("userId").asText());
        assertFalse(body.contains(PASSWORD));
        assertTrue(passwords.matches(PASSWORD, users.findById(administratorId).orElseThrow().getPasswordHash()));
        String adminToken = login("administrator@example.test", companyId);
        mvc.perform(get("/api/v1/companies/{id}/memberships", companyId).header("Authorization", adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertEquals(1, memberships.countActiveAdministrators(companyId));
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE company_id = ?", Long.class, companyId));
    }

    @Test
    void onboardingLinksExistingUsersWithoutChangingCredentials() throws Exception {
        String root = platformToken();
        User existing = user("existing");
        String originalHash = existing.getPasswordHash();
        String body = mvc.perform(post("/api/v1/companies/onboarding").header("Authorization", root)
                .contentType(MediaType.APPLICATION_JSON).content(onboarding("existing-user-company", Map.of("existingUserId", existing.getId()))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID companyId = UUID.fromString(json.readTree(body).get("company").get("id").asText());
        assertEquals(originalHash, users.findById(existing.getId()).orElseThrow().getPasswordHash());
        assertEquals(2, users.count());
        login(existing.getEmail(), companyId);
    }

    @Test
    void invalidIdentitySelectionAndDuplicateEmailsDoNotLeavePartialCompanies() throws Exception {
        String root = platformToken();
        User existing = user("existing");
        Map<String, Object> newUser = Map.of("email", existing.getEmail(), "password", PASSWORD, "fullName", "Administrator");
        for (Object selection : List.of(Map.of(), Map.of("existingUserId", existing.getId(), "newUser", newUser))) {
            mvc.perform(post("/api/v1/companies/onboarding").header("Authorization", root)
                    .contentType(MediaType.APPLICATION_JSON).content(onboarding("invalid-company", selection)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(post("/api/v1/companies/onboarding").header("Authorization", root)
                .contentType(MediaType.APPLICATION_JSON).content(onboarding("duplicate-user-company", Map.of("newUser", newUser))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
        assertEquals(0, companies.count());
        assertEquals(0, memberships.count());
        assertEquals(2, users.count());
    }

    @Test
    void utf8PasswordLimitRollsBackTheEntireOnboarding() throws Exception {
        String root = platformToken();
        mvc.perform(post("/api/v1/companies/onboarding").header("Authorization", root)
                .contentType(MediaType.APPLICATION_JSON).content(onboarding("long-password-company", Map.of("newUser", Map.of(
                    "email", "new@example.test", "password", "é".repeat(40), "fullName", "Administrator")))))
            .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("PASSWORD_BYTE_LIMIT_EXCEEDED"));
        assertEquals(0, companies.count());
        assertEquals(1, users.count());
    }

    @Test
    void legacyPendingCompanyCanReceiveItsFirstAdministrator() throws Exception {
        String root = platformToken();
        Company company = companies.save(Company.create("Legacy", "Legacy", "legacy-company", "legacy@example.test"));
        User administrator = user("administrator");
        String request = json.writeValueAsString(Map.of("existingUserId", administrator.getId()));
        mvc.perform(post("/api/v1/companies/{id}/initial-administrator", company.getId()).header("Authorization", root)
                .contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isOk()).andExpect(jsonPath("$.company.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/companies/{id}/initial-administrator", company.getId()).header("Authorization", root)
                .contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPANY_ADMIN_ALREADY_ASSIGNED"));
        login(administrator.getEmail(), company.getId());
    }

    @Test
    void signedRoleClaimsCannotGrantPlatformPrivileges() throws Exception {
        Fixture fixture = fixture();
        String forgedRoleToken = "Bearer " + jwt.generateToken(fixture.admin().getId(), fixture.company().getId(),
            List.of("COMPANY_ADMIN", "PLATFORM_ADMIN"));
        mvc.perform(post("/api/v1/companies/onboarding").header("Authorization", forgedRoleToken)
                .contentType(MediaType.APPLICATION_JSON).content(onboarding("unauthorized-company", Map.of("existingUserId", fixture.admin().getId()))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", fixture.admin().getEmail(), "password", PASSWORD))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedPlatformRolesInvalidatePreviouslyIssuedTokens() throws Exception {
        String token = platformToken();
        platformRoles.deleteAll();
        mvc.perform(get("/api/companies").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateCompanySlugsDoNotCreateOrphanAdministrators() throws Exception {
        String root = platformToken();
        User existing = user("existing");
        mvc.perform(post("/api/v1/companies/onboarding").header("Authorization", root)
                .contentType(MediaType.APPLICATION_JSON).content(onboarding("duplicate-slug", Map.of("existingUserId", existing.getId()))))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/companies/onboarding").header("Authorization", root)
                .contentType(MediaType.APPLICATION_JSON).content(onboarding("duplicate-slug", Map.of("newUser", Map.of(
                    "email", "orphan@example.test", "password", PASSWORD, "fullName", "Administrator")))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPANY_SLUG_ALREADY_EXISTS"));
        assertEquals(1, companies.count());
        assertTrue(users.findByEmailAndDeletedAtIsNull("orphan@example.test").isEmpty());
    }

    private String onboarding(String slug, Object administrator) throws Exception {
        return json.writeValueAsString(Map.of("company", Map.of("legalName", "Example Limited", "displayName", "Example",
            "slug", slug, "contactEmail", "contact@example.test"), "administrator", administrator));
    }
}
