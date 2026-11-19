package com.slotix.reservationcore.integration.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class GoogleCalendarClient {
    public static final String SCOPE = "https://www.googleapis.com/auth/calendar.app.created";
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final SecretKeySpec key;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final SecureRandom RANDOM = new SecureRandom();

    public GoogleCalendarClient(@Value("${app.google-calendar.client-id:}") String clientId,
                                @Value("${app.google-calendar.client-secret:}") String clientSecret,
                                @Value("${app.google-calendar.redirect-uri:}") String redirectUri,
                                @Value("${app.google-calendar.encryption-key:}") String encryptionKey) {
        this.clientId=clientId; this.clientSecret=clientSecret; this.redirectUri=redirectUri;
        if(encryptionKey.isBlank()) key=null;
        else {
            byte[] decoded=Base64.getDecoder().decode(encryptionKey);
            if(decoded.length!=32) throw new IllegalArgumentException("Google Calendar encryption requires a 32-byte key");
            key=new SecretKeySpec(decoded,"AES");
        }
        if(!redirectUri.isBlank()) {
            URI redirect=URI.create(redirectUri);
            boolean local="http".equals(redirect.getScheme()) && ("localhost".equals(redirect.getHost()) || "127.0.0.1".equals(redirect.getHost()));
            if((!"https".equals(redirect.getScheme()) && !local) || redirect.getHost()==null || redirect.getUserInfo()!=null
                || redirect.getQuery()!=null || redirect.getFragment()!=null) throw new IllegalArgumentException("Invalid Google Calendar redirect URI");
        }
    }

    public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank() && key!=null; }
    public void requireConfigured() {
        if(!configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,ApiErrorCode.CALENDAR_NOT_CONFIGURED,"Google Calendar is not configured");
    }
    public String authorizationUrl(String state,String verifier) {
        requireConfigured();
        return "https://accounts.google.com/o/oauth2/v2/auth?"+form(Map.of("client_id",clientId,"redirect_uri",redirectUri,
            "response_type","code","scope",SCOPE,"access_type","offline","prompt","consent","state",state,
            "code_challenge",Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier)),"code_challenge_method","S256"));
    }
    public JsonNode exchange(String code,String verifier) {
        return token(Map.of("client_id",clientId,"client_secret",clientSecret,"redirect_uri",redirectUri,
            "grant_type","authorization_code","code",code,"code_verifier",verifier));
    }
    public String accessToken(String refreshToken) {
        String token=token(Map.of("client_id",clientId,"client_secret",clientSecret,"grant_type","refresh_token","refresh_token",refreshToken)).path("access_token").asText();
        if(token.isBlank()) throw providerFailure(); return token;
    }
    private JsonNode token(Map<String,String> values) {
        requireConfigured();
        return send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(values))));
    }
    public String createCalendar(String token,String name,String timezone) {
        JsonNode response=calendarRequest("POST","https://www.googleapis.com/calendar/v3/calendars",token,Map.of("summary","Slotix · "+name,"timeZone",timezone));
        String id=response.path("id").asText(); if(id.isBlank()) throw providerFailure(); return id;
    }
    public void exportBooking(String token,String calendarId,UUID bookingId,Map<String,Object> event) {
        String base="https://www.googleapis.com/calendar/v3/calendars/"+encode(calendarId)+"/events";
        String id=bookingId.toString().replace("-","");
        try { calendarRequest("PUT",base+"/"+id+"?sendUpdates=none",token,event); }
        catch(MissingEvent exception) { calendarRequest("POST",base+"?sendUpdates=none",token,event); }
    }
    private JsonNode calendarRequest(String method,String url,String token,Object body) {
        try {
            return send(HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token)
                .header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))));
        } catch(com.fasterxml.jackson.core.JsonProcessingException exception) { throw providerFailure(); }
    }
    private JsonNode send(HttpRequest.Builder request) {
        try {
            var response=http.send(request.timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404) throw new MissingEvent();
            if(response.statusCode()<200 || response.statusCode()>=300 || response.body().length()>100000) throw providerFailure();
            return json.readTree(response.body());
        } catch(java.io.IOException exception) { throw providerFailure(); }
        catch(InterruptedException exception) { Thread.currentThread().interrupt(); throw providerFailure(); }
    }
    public String encrypt(UUID companyId,String value) {
        byte[] nonce=new byte[12]; RANDOM.nextBytes(nonce);
        byte[] data=crypt(Cipher.ENCRYPT_MODE,companyId,nonce,value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12+data.length).put(nonce).put(data).array());
    }
    public String decrypt(UUID companyId,String value) {
        byte[] data=Base64.getDecoder().decode(value); if(data.length<28) throw new IllegalArgumentException("Invalid encrypted calendar credential");
        ByteBuffer buffer=ByteBuffer.wrap(data); byte[] nonce=new byte[12];buffer.get(nonce);byte[] encrypted=new byte[buffer.remaining()];buffer.get(encrypted);
        return new String(crypt(Cipher.DECRYPT_MODE,companyId,nonce,encrypted),StandardCharsets.UTF_8);
    }
    private byte[] crypt(int mode,UUID companyId,byte[] nonce,byte[] value) {
        requireConfigured();
        try { Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,key,new GCMParameterSpec(128,nonce));
            cipher.updateAAD(("slotix:google-calendar:v1:"+companyId).getBytes(StandardCharsets.UTF_8));return cipher.doFinal(value);
        } catch(java.security.GeneralSecurityException exception) { throw new IllegalStateException("Calendar credential encryption failed"); }
    }
    public static String nonce() { byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String hash(String value) { return java.util.HexFormat.of().formatHex(digest(value)); }
    private static byte[] digest(String value) { try {return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));} catch(java.security.NoSuchAlgorithmException exception) {throw new IllegalStateException(exception);} }
    private static String form(Map<String,String> values) { return values.entrySet().stream().map(entry->encode(entry.getKey())+"="+encode(entry.getValue())).collect(Collectors.joining("&")); }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20"); }
    private static ApiException providerFailure() { return new ApiException(HttpStatus.BAD_GATEWAY,ApiErrorCode.CALENDAR_PROVIDER_UNAVAILABLE,"Google Calendar could not complete the request"); }
    private static class MissingEvent extends RuntimeException {}
}
