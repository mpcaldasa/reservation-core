package com.slotix.reservationcore;

import com.slotix.reservationcore.identity.IdentityIntegrationTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OperationalApiIntegrationTest extends IdentityIntegrationTestSupport {
    @Autowired private MeterRegistry metrics;

    @Test
    void healthAndOpenApiAreAvailableWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Slotix Reservation API"))
            .andExpect(jsonPath("$.paths['/api/v1/bookings']").exists());
    }

    @Test
    void platformAdministratorCanQueryAuditAndFailedDeliveries() throws Exception {
        Fixture fixture = fixture();
        String token = platformToken();
        jdbc.update("INSERT INTO audit_logs(company_id, actor_user_id, action, entity_type, entity_id) "
            + "VALUES (?, ?, 'RESOURCE_CREATED', 'RESOURCE', ?)", fixture.company().getId(),
            fixture.admin().getId(), java.util.UUID.randomUUID());
        DeliveryFixture delivery = createFailedDelivery(fixture);

        mvc.perform(get("/api/platform/operations/audit").header("Authorization", token)
                .param("companyId", fixture.company().getId().toString()).param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].action").value("RESOURCE_CREATED"));
        mvc.perform(get("/api/platform/operations/audit").header("Authorization", fixture.adminToken()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/operations/notification-deliveries").header("Authorization", token)
                .param("status", "invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_STATUS_INVALID"));
        mvc.perform(get("/api/platform/operations/audit").header("Authorization", token).param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("OPERATIONAL_PAGE_INVALID"));
        mvc.perform(get("/api/platform/operations/notification-deliveries").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(delivery.id().toString()))
            .andExpect(jsonPath("$.items[0].errorCode").value("SMTP_UNAVAILABLE"));
        mvc.perform(post("/api/platform/operations/notification-deliveries/" + delivery.id() + "/replay")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.attempts").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = "
            + "'NOTIFICATION_DELIVERY_REPLAYED'", Integer.class)).isEqualTo(1);
        assertThat(metrics.find("jdbc.query").timer()).isNotNull();
        assertThat(metrics.find("jdbc.query").timer().count()).isPositive();
    }

    private DeliveryFixture createFailedDelivery(Fixture fixture) {
        UUID resourceId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("INSERT INTO resources(id, company_id, name, resource_type, capacity, status) "
            + "VALUES (?, ?, 'Operations Test Resource', 'SPACE', 1, 'ACTIVE')", resourceId, fixture.company().getId());
        jdbc.update("INSERT INTO bookings(id, company_id, customer_user_id, resource_id, start_at, end_at, status, timezone) "
            + "VALUES (?, ?, ?, ?, now() + interval '1 day', now() + interval '1 day 1 hour', 'PENDING', 'UTC')",
            bookingId, fixture.company().getId(), fixture.customer().getId(), resourceId);
        jdbc.update("INSERT INTO outbox_events(id, company_id, aggregate_type, aggregate_id, event_type, payload) "
            + "VALUES (?, ?, 'BOOKING', ?, 'BOOKING_CREATED', '{}'::jsonb)", eventId, fixture.company().getId(), bookingId);
        jdbc.update("INSERT INTO notification_deliveries(id, company_id, booking_id, outbox_event_id, channel, template, "
            + "recipient, status, attempts, error_message) VALUES (?, ?, ?, ?, 'EMAIL', 'BOOKING_CREATED', "
            + "'customer@example.test', 'FAILED', 5, 'SMTP_UNAVAILABLE')", deliveryId, fixture.company().getId(),
            bookingId, eventId);
        return new DeliveryFixture(deliveryId);
    }

    private record DeliveryFixture(UUID id) {}
}
