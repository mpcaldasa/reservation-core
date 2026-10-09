package com.slotix.reservationcore.identity;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyMembershipApiIntegrationTest extends IdentityIntegrationTestSupport {
    @Test
    void existingUsersCanJoinAnotherCompanyWithoutChangingTheirIdentity() throws Exception {
        Fixture first = fixture();
        Fixture second = fixture();
        String originalHash = first.customer().getPasswordHash();
        String request = memberRequest(first.customer().getId());
        mvc.perform(post(path(second)).header("Authorization", second.adminToken()).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(first.customer().getId().toString()))
            .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
        mvc.perform(post(path(second)).header("Authorization", second.adminToken()).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBERSHIP_ALREADY_EXISTS"));
        assertEquals(originalHash, users.findById(first.customer().getId()).orElseThrow().getPasswordHash());
        assertEquals(4, users.count());
        login(first.customer().getEmail(), first.company().getId());
        login(first.customer().getEmail(), second.company().getId());
    }

    @Test
    void membershipListingsArePagedAndScopedToTheAuthorizedCompany() throws Exception {
        Fixture first = fixture();
        Fixture other = fixture();
        mvc.perform(get(path(first)).param("page", "0").param("size", "1").header("Authorization", first.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].companyId").value(first.company().getId().toString()))
            .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(path(first)).param("size", "101").header("Authorization", first.adminToken()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEMBERSHIP_PAGE_INVALID"));
        mvc.perform(get(path(first)).param("page", "-1").header("Authorization", first.adminToken()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEMBERSHIP_PAGE_INVALID"));
        mvc.perform(get(path(first)).header("Authorization", first.customerToken())).andExpect(status().isForbidden());
        mvc.perform(get(path(first)).header("Authorization", other.adminToken())).andExpect(status().isForbidden());
        mvc.perform(get(path(first))).andExpect(status().isUnauthorized());
        mvc.perform(put(path(first) + "/" + other.customerMembership().getId() + "/roles")
                .header("Authorization", first.adminToken()).contentType(MediaType.APPLICATION_JSON).content(rolesRequest("CUSTOMER")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBERSHIP_NOT_FOUND"));
    }

    @Test
    void roleChangesApplyToPreviouslyIssuedTokensAndDoNotDuplicateAudit() throws Exception {
        Fixture fixture = fixture();
        String rolePath = path(fixture) + "/" + fixture.customerMembership().getId() + "/roles";
        mvc.perform(get(path(fixture)).header("Authorization", fixture.customerToken())).andExpect(status().isForbidden());
        String first = mvc.perform(put(rolePath).header("Authorization", fixture.adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(rolesRequest("COMPANY_ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String repeat = mvc.perform(put(rolePath).header("Authorization", fixture.adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(rolesRequest("COMPANY_ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first).get("updatedAt"), json.readTree(repeat).get("updatedAt"));
        assertEquals(1, auditCount(fixture));
        mvc.perform(get(path(fixture)).header("Authorization", fixture.customerToken())).andExpect(status().isOk());
        mvc.perform(put(rolePath).header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(rolesRequest("CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(get(path(fixture)).header("Authorization", fixture.customerToken())).andExpect(status().isForbidden());
        assertEquals(2, auditCount(fixture));
    }

    @Test
    void suspensionInvalidatesAnExistingTokenAndActivationRestoresCompanyAccess() throws Exception {
        Fixture fixture = fixture();
        String membership = path(fixture) + "/" + fixture.customerMembership().getId();
        mvc.perform(post(membership + "/suspend").header("Authorization", fixture.adminToken())).andExpect(status().isOk());
        mvc.perform(post(membership + "/suspend").header("Authorization", fixture.adminToken())).andExpect(status().isOk());
        mvc.perform(get(resourcesPath(fixture)).header("Authorization", fixture.customerToken())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
            "email", fixture.customer().getEmail(), "password", PASSWORD, "companyId", fixture.company().getId()))))
            .andExpect(status().isUnauthorized());
        assertEquals(1, auditCount(fixture));
        mvc.perform(post(membership + "/activate").header("Authorization", fixture.adminToken())).andExpect(status().isOk());
        mvc.perform(post(membership + "/activate").header("Authorization", fixture.adminToken())).andExpect(status().isOk());
        mvc.perform(get(resourcesPath(fixture)).header("Authorization", fixture.customerToken())).andExpect(status().isOk());
        assertEquals(2, auditCount(fixture));
    }

    @Test
    void suspendingOneMembershipDoesNotSuspendTheSameUserInAnotherCompany() throws Exception {
        Fixture first = fixture();
        Fixture second = fixture();
        String response = mvc.perform(post(path(second)).header("Authorization", second.adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(memberRequest(first.customer().getId())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String secondToken = login(first.customer().getEmail(), second.company().getId());
        mvc.perform(post(path(second) + "/" + json.readTree(response).get("id").asText() + "/suspend")
                .header("Authorization", second.adminToken())).andExpect(status().isOk());
        mvc.perform(get(resourcesPath(first)).header("Authorization", first.customerToken())).andExpect(status().isOk());
        mvc.perform(get(resourcesPath(second)).header("Authorization", secondToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void theLastAdministratorCannotBeSuspendedOrDemoted() throws Exception {
        Fixture fixture = fixture();
        String administrator = path(fixture) + "/" + fixture.adminMembership().getId();
        mvc.perform(post(administrator + "/suspend").header("Authorization", fixture.adminToken()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_COMPANY_ADMIN_REQUIRED"));
        mvc.perform(put(administrator + "/roles").header("Authorization", fixture.adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(rolesRequest("CUSTOMER")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_COMPANY_ADMIN_REQUIRED"));
        assertEquals(1, memberships.countActiveAdministrators(fixture.company().getId()));
        assertEquals(0, auditCount(fixture));
    }

    @Test
    void concurrentSelfDemotionsRetainOneActiveAdministrator() throws Exception {
        Fixture fixture = fixture();
        mvc.perform(put(path(fixture) + "/" + fixture.customerMembership().getId() + "/roles")
                .header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON).content(rolesRequest("COMPANY_ADMIN")))
            .andExpect(status().isOk());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> demoteAfter(start, fixture, fixture.adminMembership().getId(), fixture.adminToken()));
            var second = executor.submit(() -> demoteAfter(start, fixture, fixture.customerMembership().getId(), fixture.customerToken()));
            start.countDown();
            assertEquals(Set.of(200, 409), Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
        }
        assertEquals(1, memberships.countActiveAdministrators(fixture.company().getId()));
        assertEquals(2, auditCount(fixture));
    }

    @Test
    void concurrentDuplicateLinksProduceOneMembership() throws Exception {
        Fixture fixture = fixture();
        User user = user("new-member");
        CountDownLatch start = new CountDownLatch(1);
        String request = memberRequest(user.getId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> linkAfter(start, fixture, request));
            var second = executor.submit(() -> linkAfter(start, fixture, request));
            start.countDown();
            assertEquals(Set.of(201, 409), Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
        }
        assertEquals(3, memberships.count());
        assertEquals(1, auditCount(fixture));
    }

    @Test
    void administratorsCanRegisterNewUsersAndDuplicatesAreConflicts() throws Exception {
        Fixture fixture = fixture();
        String request = json.writeValueAsString(Map.of("companyId", fixture.company().getId(), "email", "New@EXAMPLE.test",
            "password", PASSWORD, "fullName", "New Customer", "roles", List.of("CUSTOMER")));
        mvc.perform(post("/api/users").header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.test"));
        mvc.perform(post("/api/users").header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
        login("new@example.test", fixture.company().getId());
        assertEquals(3, users.count());
        assertEquals(3, memberships.count());
        assertEquals(1, auditCount(fixture));
    }

    @Test
    void invitationRequiresConfiguredEmailDelivery() throws Exception {
        Fixture fixture = fixture();
        mvc.perform(post(path(fixture) + "/invitations").header("Authorization", fixture.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "invite@example.test", "roles", List.of("CUSTOMER")))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_UNAVAILABLE"));
        assertEquals(0, jdbc.queryForObject("select count(*) from company_invitations", Integer.class));
    }

    @Test
    void passwordResetRequestsAreGenericWhenEmailIsDisabled() throws Exception {
        String known = mvc.perform(post("/api/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "known@example.test"))))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "unknown@example.test"))))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(known, unknown);
        assertEquals(0, jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class));
    }

    @Test
    void invalidRolesAndInactiveUsersCannotBeAdded() throws Exception {
        Fixture fixture = fixture();
        User user = user("inactive");
        mvc.perform(post(path(fixture)).header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("userId", user.getId(), "roles", List.of())))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", user.getId());
        mvc.perform(post(path(fixture)).header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(memberRequest(user.getId()))).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACTIVE_USER_NOT_FOUND"));
        assertEquals(2, memberships.count());
    }

    @Test
    void inactiveAccountsAndCompaniesCannotUseExistingTokens() throws Exception {
        Fixture fixture = fixture();
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", fixture.customer().getId());
        mvc.perform(get(resourcesPath(fixture)).header("Authorization", fixture.customerToken())).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE companies SET status = 'SUSPENDED' WHERE id = ?", fixture.company().getId());
        mvc.perform(get(path(fixture)).header("Authorization", fixture.adminToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void signedRoleClaimsCannotGrantCompanyAdministration() throws Exception {
        Fixture fixture = fixture();
        String token = "Bearer " + jwt.generateToken(fixture.customer().getId(), fixture.company().getId(), List.of("COMPANY_ADMIN"));
        mvc.perform(get(path(fixture)).header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get(path(fixture)).header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    private int demoteAfter(CountDownLatch start, Fixture fixture, UUID membershipId, String token) throws Exception {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return mvc.perform(put(path(fixture) + "/" + membershipId + "/roles").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(rolesRequest("CUSTOMER"))).andReturn().getResponse().getStatus();
    }

    private int linkAfter(CountDownLatch start, Fixture fixture, String request) throws Exception {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return mvc.perform(post(path(fixture)).header("Authorization", fixture.adminToken()).contentType(MediaType.APPLICATION_JSON)
            .content(request)).andReturn().getResponse().getStatus();
    }

    private String memberRequest(UUID userId) throws Exception {
        return json.writeValueAsString(Map.of("userId", userId, "roles", List.of("CUSTOMER")));
    }

    private String rolesRequest(String role) throws Exception {
        return json.writeValueAsString(Map.of("roles", List.of(role)));
    }

    private String path(Fixture fixture) {
        return "/api/v1/companies/" + fixture.company().getId() + "/memberships";
    }

    private String resourcesPath(Fixture fixture) {
        return "/api/v1/companies/" + fixture.company().getId() + "/resources";
    }

    private int auditCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE company_id = ?", Integer.class, fixture.company().getId());
    }
}
