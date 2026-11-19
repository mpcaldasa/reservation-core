package com.slotix.reservationcore.customer;

import com.slotix.reservationcore.identity.IdentityIntegrationTestSupport;
import com.slotix.reservationcore.identity.CompanyMembership;
import com.slotix.reservationcore.identity.MembershipRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.Set;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerDirectoryApiIntegrationTest extends IdentityIntegrationTestSupport {
    @Test
    void staffCanSearchNamesAndUpdateCompanyLocalContactDetails() throws Exception {
        var first = fixture();
        var second = fixture();
        memberships.save(CompanyMembership.create(second.company().getId(), first.customer().getId(), Set.of(MembershipRole.CUSTOMER)));
        String path = "/api/v1/companies/" + first.company().getId() + "/customers";
        mvc.perform(get(path).param("query", "customer").header("Authorization", first.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].fullName").value("customer"));
        mvc.perform(put(path + "/" + first.customer().getId()).header("Authorization", first.adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+57 300 1234567\",\"notes\":\"Prefers mornings\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+57 300 1234567"));
        mvc.perform(get("/api/v1/companies/" + second.company().getId() + "/customers/" + first.customer().getId())
                .header("Authorization", second.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.notes").value(""));
        mvc.perform(get(path).param("query", "%").header("Authorization", first.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(path).param("size", "1").header("Authorization", first.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void directoryAndHistoryEnforceTenantAndStaffPermissions() throws Exception {
        var first = fixture();
        var other = fixture();
        String path = "/api/v1/companies/" + first.company().getId() + "/customers";
        mvc.perform(get(path).header("Authorization", first.customerToken())).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", other.adminToken())).andExpect(status().isForbidden());
        mvc.perform(get(path + "/" + other.customer().getId()).header("Authorization", first.adminToken()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        mvc.perform(get(path + "/" + first.customer().getId() + "/bookings").header("Authorization", first.adminToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(path).param("page", "-1").header("Authorization", first.adminToken())).andExpect(status().isBadRequest());
        mvc.perform(put(path + "/" + first.customer().getId()).header("Authorization", first.adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"<script>\",\"notes\":\"\"}"))
            .andExpect(status().isBadRequest());
        var manager = user("manager");
        memberships.save(CompanyMembership.create(first.company().getId(), manager.getId(), Set.of(MembershipRole.BOOKING_MANAGER)));
        mvc.perform(get(path).header("Authorization", token(manager, first.company(), MembershipRole.BOOKING_MANAGER)))
            .andExpect(status().isOk());
    }
}
