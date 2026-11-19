package com.slotix.reservationcore.integration.google;

import com.slotix.reservationcore.identity.IdentityIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import java.net.URI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties={"app.google-calendar.client-id=local-test-id","app.google-calendar.client-secret=local-test-secret",
    "app.google-calendar.redirect-uri=http://localhost:4200/integrations/google/callback",
    "app.google-calendar.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class GoogleCalendarApiIntegrationTest extends IdentityIntegrationTestSupport {
    @Test
    void connectionIsAdministratorOnlyAndStateIsTenantBoundWithoutCallingGoogle() throws Exception {
        var fixture=fixture(); var other=fixture();
        String path="/api/v1/companies/"+fixture.company().getId()+"/google-calendar";
        mvc.perform(get(path).header("Authorization",fixture.customerToken())).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization",other.adminToken())).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization",fixture.adminToken())).andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.connected").value(false));
        String result=mvc.perform(post(path+"/connect").header("Authorization",fixture.adminToken()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url=json.readTree(result).get("authorizationUrl").asText();
        assertEquals("accounts.google.com",URI.create(url).getHost());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM google_calendar_oauth_states WHERE company_id=?",Integer.class,fixture.company().getId()));
        mvc.perform(post(path+"/complete").header("Authorization",fixture.adminToken()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"state\":\"wrong-state\",\"code\":\"unused\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CALENDAR_AUTHORIZATION_FAILED"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM google_calendar_oauth_states WHERE company_id=?",Integer.class,fixture.company().getId()));
        mvc.perform(delete(path).header("Authorization",fixture.adminToken())).andExpect(status().isNoContent());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM google_calendar_oauth_states WHERE company_id=?",Integer.class,fixture.company().getId()));
    }
}
