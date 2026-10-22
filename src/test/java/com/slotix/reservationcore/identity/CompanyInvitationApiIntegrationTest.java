package com.slotix.reservationcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.notification.email.enabled=true",
    "app.notification.email.from=slotix@example.test",
    "app.identity.invitation.acceptance-url=https://app.example.test/accept-invitation",
    "app.identity.password-reset.acceptance-url=https://app.example.test/reset-password"
})
@AutoConfigureMockMvc
class CompanyInvitationApiIntegrationTest extends IdentityIntegrationTestSupport {
    private static final Pattern INVITATION_TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]{43})");

    @Autowired private IdentityEmailWorker emailWorker;
    @MockitoBean private JavaMailSender smtp;

    @Test
    void administratorCanInviteAndNewUserCanAcceptOnce() throws Exception {
        Fixture fixture = fixture();
        String invitationPath = "/api/v1/companies/" + fixture.company().getId() + "/memberships/invitations";
        MvcResult created = mvc.perform(MockMvcRequestBuilders.post(invitationPath)
                .header("Authorization", fixture.adminToken()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "Invitee@Example.test", "roles", List.of("CUSTOMER")))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.email").value("invitee@example.test"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();
        org.junit.jupiter.api.Assertions.assertFalse(created.getResponse().getContentAsString().contains("token"));
        String encrypted = jdbc.queryForObject("select token from identity_email_deliveries limit 1", String.class);
        org.junit.jupiter.api.Assertions.assertTrue(encrypted.startsWith("v1:"));
        org.junit.jupiter.api.Assertions.assertFalse(encrypted.matches("[A-Za-z0-9_-]{43}"));
        mvc.perform(MockMvcRequestBuilders.post(invitationPath)
                .header("Authorization", fixture.adminToken()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "invitee@example.test", "roles", List.of("CUSTOMER")))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_EXISTS"));

        emailWorker.sendDueEmails();
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
            "select count(*) from identity_email_deliveries where token is not null", Integer.class));
        Matcher tokenMatcher = INVITATION_TOKEN.matcher(message.getValue().getText());
        org.junit.jupiter.api.Assertions.assertTrue(tokenMatcher.find(), "Invitation email should contain a one-time token");
        String token = tokenMatcher.group(1);

        mvc.perform(MockMvcRequestBuilders.post("/api/invitations/accept")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", PASSWORD, "fullName", "Invited Member"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("invitee@example.test"))
            .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
        login("invitee@example.test", fixture.company().getId());

        mvc.perform(MockMvcRequestBuilders.post("/api/invitations/accept")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", PASSWORD, "fullName", "Invited Member"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_ACCEPTED"));
    }

    @Test
    void invalidInvitationTokenReturnsMachineReadableError() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/invitations/accept")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", "invalid-token", "password", PASSWORD, "fullName", "Invited Member"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
    }

    @Test
    void failedInvitationEmailKeepsDeliveryRetryableWithoutExposingToken() throws Exception {
        Fixture fixture = fixture();
        String invitationPath = "/api/v1/companies/" + fixture.company().getId() + "/memberships/invitations";
        mvc.perform(MockMvcRequestBuilders.post(invitationPath)
                .header("Authorization", fixture.adminToken()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "retry@example.test", "roles", List.of("CUSTOMER")))))
            .andExpect(status().isAccepted());
        doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
            .when(smtp).send(any(SimpleMailMessage.class));

        emailWorker.sendDueEmails();

        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
            "select count(*) from identity_email_deliveries where status = 'FAILED' and attempts = 1 and token is not null",
            Integer.class));
    }

    @Test
    void passwordResetDoesNotRevealAccountExistenceAndInvalidatesOldSessions() throws Exception {
        Fixture fixture = fixture();
        String resetRequest = json.writeValueAsString(Map.of("email", fixture.customer().getEmail()));
        var knownAccount = mvc.perform(MockMvcRequestBuilders.post("/api/password-resets")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(resetRequest))
            .andExpect(status().isAccepted()).andReturn();
        var unknownAccount = mvc.perform(MockMvcRequestBuilders.post("/api/password-resets")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "unknown@example.test"))))
            .andExpect(status().isAccepted()).andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(knownAccount.getResponse().getContentAsString(),
            unknownAccount.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class));

        emailWorker.sendDueEmails();
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        Matcher tokenMatcher = INVITATION_TOKEN.matcher(message.getValue().getText());
        org.junit.jupiter.api.Assertions.assertTrue(tokenMatcher.find(), "Reset email should contain a one-time token");
        String token = tokenMatcher.group(1);
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
            "select count(*) from identity_email_deliveries where token is not null", Integer.class));

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/companies/" + fixture.company().getId() + "/memberships")
                .header("Authorization", fixture.customerToken()))
            .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post("/api/password-resets/confirm")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", "new-correct-horse-88"))))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertTrue(passwords.matches("new-correct-horse-88",
            users.findById(fixture.customer().getId()).orElseThrow().getPasswordHash()));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/companies/" + fixture.company().getId() + "/memberships")
                .header("Authorization", fixture.customerToken()))
            .andExpect(status().isUnauthorized());

        mvc.perform(MockMvcRequestBuilders.post("/api/users/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", fixture.customer().getEmail(), "password", PASSWORD,
                    "companyId", fixture.company().getId()))))
            .andExpect(status().isUnauthorized());
        String newLogin = mvc.perform(MockMvcRequestBuilders.post("/api/users/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", fixture.customer().getEmail(),
                    "password", "new-correct-horse-88", "companyId", fixture.company().getId()))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newToken = "Bearer " + json.readTree(newLogin).get("token").asText();
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/companies/" + fixture.company().getId() + "/memberships")
                .header("Authorization", newToken))
            .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post("/api/password-resets/confirm")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", "another-correct-horse"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PASSWORD_RESET_ALREADY_USED"));
    }
}
