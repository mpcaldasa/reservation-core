package com.slotix.reservationcore.integration.google;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GoogleCalendarClientTest {
    private final GoogleCalendarClient client=new GoogleCalendarClient("client-id","secret",
        "http://localhost:4200/integrations/google/callback","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    @Test
    void credentialsAreRandomizedAndBoundToTheirTenant() {
        UUID company=UUID.randomUUID();
        String first=client.encrypt(company,"refresh-secret");
        assertNotEquals(first,client.encrypt(company,"refresh-secret"));
        assertFalse(first.contains("refresh-secret"));
        assertEquals("refresh-secret",client.decrypt(company,first));
        assertThrows(IllegalStateException.class,()->client.decrypt(UUID.randomUUID(),first));
    }
    @Test
    void authorizationUsesOfflineConsentPkceAndTheRestrictedCalendarScope() {
        URI uri=URI.create(client.authorizationUrl("state","verifier"));
        String query=URLDecoder.decode(uri.getRawQuery(),StandardCharsets.UTF_8);
        assertEquals("accounts.google.com",uri.getHost());
        assertTrue(query.contains("code_challenge_method=S256"));
        assertTrue(query.contains("scope="+GoogleCalendarClient.SCOPE));
        assertTrue(query.contains("access_type=offline"));
        assertFalse(query.contains("client_secret"));
        assertFalse(query.contains("code_challenge=verifier"));
    }
    @Test
    void rejectsUnsafeRedirectsAndMissingConfiguration() {
        assertThrows(IllegalArgumentException.class,()->new GoogleCalendarClient("id","secret","http://example.com/callback",""));
        var disabled=new GoogleCalendarClient("","","",""); assertFalse(disabled.configured());
        assertThrows(com.slotix.reservationcore.common.ApiException.class,()->disabled.authorizationUrl("state","verifier"));
    }
}
