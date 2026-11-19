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
}
