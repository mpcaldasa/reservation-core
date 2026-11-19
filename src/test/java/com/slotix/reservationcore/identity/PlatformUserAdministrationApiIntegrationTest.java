package com.slotix.reservationcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformUserAdministrationApiIntegrationTest extends IdentityIntegrationTestSupport {
    @Test
    void platformAdministratorCanGrantAndRevokePlatformRolesSafely() throws Exception {
        Fixture fixture = fixture();
        String rootToken = platformToken();
        String rootId = jwt.parseAndValidate(rootToken.substring(7)).getSubject();
        String customerPlatformPath = "/api/platform/users/" + fixture.customer().getId() + "/platform-admin";

        mvc.perform(MockMvcRequestBuilders.delete("/api/platform/users/" + rootId + "/platform-admin")
                .header("Authorization", rootToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LAST_PLATFORM_ADMIN_REQUIRED"));
        mvc.perform(MockMvcRequestBuilders.post(customerPlatformPath).header("Authorization", rootToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platformRoles[0]").value("PLATFORM_ADMIN"));
        mvc.perform(MockMvcRequestBuilders.delete(customerPlatformPath).header("Authorization", rootToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platformRoles.length()").value(0));
        mvc.perform(MockMvcRequestBuilders.post(customerPlatformPath).header("Authorization", rootToken))
            .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.post("/api/platform/users/" + fixture.customer().getId() + "/deactivate")
                .header("Authorization", rootToken))
            .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.post(customerPlatformPath).header("Authorization", rootToken))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("PLATFORM_ADMIN_REQUIRES_ACTIVE_USER"));
    }

    @Test
    void platformAdministratorCanListDeactivateAndReactivateAccounts() throws Exception {
        Fixture fixture = fixture();
        String platformToken = platformToken();
        String companyPath = "/api/v1/companies/" + fixture.company().getId() + "/memberships";
        String userPath = "/api/platform/users/" + fixture.customer().getId();

        mvc.perform(MockMvcRequestBuilders.get("/api/platform/users").header("Authorization", platformToken)
                .param("page", "0").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(MockMvcRequestBuilders.get("/api/platform/users").header("Authorization", platformToken)
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PLATFORM_USER_PAGE_INVALID"));
        mvc.perform(MockMvcRequestBuilders.post("/api/platform/users/" + jwt.parseAndValidate(platformToken.substring(7)).getSubject()
                + "/deactivate").header("Authorization", platformToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LAST_PLATFORM_ADMIN_REQUIRED"));

        mvc.perform(MockMvcRequestBuilders.get(companyPath).header("Authorization", fixture.customerToken()))
            .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post(userPath + "/deactivate").header("Authorization", platformToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(MockMvcRequestBuilders.get(companyPath).header("Authorization", fixture.customerToken()))
            .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post(userPath + "/reactivate").header("Authorization", platformToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(MockMvcRequestBuilders.get(companyPath).header("Authorization", fixture.customerToken()))
            .andExpect(status().isUnauthorized());

        String refreshedToken = login(fixture.customer().getEmail(), fixture.company().getId());
        mvc.perform(MockMvcRequestBuilders.get(companyPath).header("Authorization", refreshedToken))
            .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post("/api/platform/users/" + java.util.UUID.randomUUID() + "/deactivate")
                .header("Authorization", platformToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PLATFORM_USER_NOT_FOUND"));
    }
}
