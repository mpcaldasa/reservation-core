package com.slotix.reservationcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.Set;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CurrentUserApiIntegrationTest extends IdentityIntegrationTestSupport {
    @Test
    void namesAndWorkspaceSelectionIncludeOnlyActiveMembershipsAndResolveTargetRoles() throws Exception {
        var first=fixture(); var other=fixture();
        mvc.perform(get("/api/me").header("Authorization",first.customerToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("customer"))
            .andExpect(jsonPath("$.workspaces.length()").value(1))
            .andExpect(jsonPath("$.workspaces[0].companyName").value(first.company().getDisplayName()));
        String target="{\"companyId\":\""+other.company().getId()+"\"}";
        mvc.perform(post("/api/me/company").header("Authorization",first.adminToken()).contentType(MediaType.APPLICATION_JSON).content(target))
            .andExpect(status().isForbidden());
        memberships.save(CompanyMembership.create(other.company().getId(),first.admin().getId(),Set.of(MembershipRole.CUSTOMER)));
        String body=mvc.perform(post("/api/me/company").header("Authorization",first.adminToken()).contentType(MediaType.APPLICATION_JSON).content(target))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token=json.readTree(body).get("token").asText();
        mvc.perform(get("/api/v1/companies/"+other.company().getId()+"/customers").header("Authorization","Bearer "+token))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.companyId").value(other.company().getId().toString()));
    }
    @Test
    void refreshedCredentialsRemainValidWhenSelectingAnotherWorkspace() throws Exception {
        var first=fixture();var other=fixture();
        memberships.save(CompanyMembership.create(other.company().getId(),first.admin().getId(),Set.of(MembershipRole.CUSTOMER)));
        first.admin().changePasswordHash(passwords.encode(PASSWORD),java.time.Instant.now());
        users.saveAndFlush(first.admin());
        mvc.perform(get("/api/me").header("Authorization",first.adminToken())).andExpect(status().isUnauthorized());
        String fresh=login(first.admin().getEmail(),first.company().getId());
        String body=mvc.perform(post("/api/me/company").header("Authorization",fresh).contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyId\":\""+other.company().getId()+"\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token=json.readTree(body).get("token").asText();
        org.junit.jupiter.api.Assertions.assertEquals(1,jwt.parseAndValidate(token).get("credentialVersion",Integer.class));
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    }

}
