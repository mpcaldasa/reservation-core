package com.slotix.reservationcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OperationalApiIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired private MockMvc mvc;

    @Test
    void healthAndOpenApiAreAvailableWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Slotix Reservation API"))
            .andExpect(jsonPath("$.paths['/api/v1/bookings']").exists());
    }
}
