package com.slotix.reservationcore.integration.google;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.booking.BookingRepository;
import com.slotix.reservationcore.common.*;
import com.slotix.reservationcore.identity.IdentityRateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/google-calendar")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class GoogleCalendarController {
    private final GoogleCalendarClient google;
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;
    private final IdentityRateLimiter limits;
    private final BookingRepository bookings;
    private final AuditService audit;

    public GoogleCalendarController(GoogleCalendarClient google,JdbcTemplate jdbc,TenantAccessService access,
                                    IdentityRateLimiter limits,BookingRepository bookings,AuditService audit) {
        this.google=google;this.jdbc=jdbc;this.access=access;this.limits=limits;this.bookings=bookings;this.audit=audit;
    }
    public record ConnectionStatus(boolean configured,boolean connected) {}
    public record Authorization(String authorizationUrl) {}
    public record Completion(@NotBlank @Size(max=100) String state,@NotBlank @Size(max=4096) String code) {}

    @GetMapping
    public ConnectionStatus status(@PathVariable UUID companyId) {
        access.requireActiveMembership(companyId);
        return new ConnectionStatus(google.configured(),connected(companyId));
    }

    @PostMapping("/connect")
    public Authorization connect(@PathVariable UUID companyId) {
        access.requireActiveMembership(companyId); google.requireConfigured();
        limits.acquire("google-connect",access.currentUserId().toString(),5,600);
        if(connected(companyId)) throw conflict();
        String state=GoogleCalendarClient.nonce(),verifier=GoogleCalendarClient.nonce();
        jdbc.update("DELETE FROM google_calendar_oauth_states WHERE expires_at<=now()");
        jdbc.update("""
            INSERT INTO google_calendar_oauth_states(state_hash,company_id,user_id,verifier_ciphertext,expires_at)
            VALUES (?,?,?,?,now()+interval '10 minutes') ON CONFLICT(company_id) DO UPDATE
                SET state_hash=excluded.state_hash,user_id=excluded.user_id,
                    verifier_ciphertext=excluded.verifier_ciphertext,expires_at=excluded.expires_at
            """,GoogleCalendarClient.hash(state),companyId,access.currentUserId(),google.encrypt(companyId,verifier));
        return new Authorization(google.authorizationUrl(state,verifier));
    }

    @PostMapping("/complete")
    public ConnectionStatus complete(@PathVariable UUID companyId,@Valid @RequestBody Completion completion) {
        access.requireActiveMembership(companyId);google.requireConfigured();
        limits.acquire("google-complete",access.currentUserId().toString(),10,600);
        if(connected(companyId)) throw conflict();
        List<String> verifiers=jdbc.query("""
            DELETE FROM google_calendar_oauth_states WHERE state_hash=? AND company_id=? AND user_id=? AND expires_at>now()
            RETURNING verifier_ciphertext
            """,(row,index)->row.getString(1),GoogleCalendarClient.hash(completion.state()),companyId,access.currentUserId());
        if(verifiers.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,ApiErrorCode.CALENDAR_AUTHORIZATION_FAILED,"The calendar connection expired or does not belong to this session");
        var tokens=google.exchange(completion.code(),google.decrypt(companyId,verifiers.getFirst()));
        String refresh=tokens.path("refresh_token").asText(),token=tokens.path("access_token").asText();
        if(refresh.isBlank() || token.isBlank() || !Arrays.asList(tokens.path("scope").asText().split(" ")).contains(GoogleCalendarClient.SCOPE))
            throw new ApiException(HttpStatus.BAD_REQUEST,ApiErrorCode.CALENDAR_AUTHORIZATION_FAILED,"Required calendar permissions or offline access were not granted");
        String[] company=jdbc.queryForObject("SELECT display_name,timezone FROM companies WHERE id=?",(row,index)->new String[]{row.getString(1),row.getString(2)},companyId);
        String calendar=google.createCalendar(token,company[0],company[1]);
        int saved=jdbc.update("""
            INSERT INTO google_calendar_connections(company_id,connected_by,calendar_id,refresh_token_ciphertext)
            VALUES (?,?,?,?) ON CONFLICT(company_id) DO NOTHING
            """,companyId,access.currentUserId(),calendar,google.encrypt(companyId,refresh));
        if(saved!=1) throw conflict();
        audit.record(companyId,access.currentUserId(),"GOOGLE_CALENDAR_CONNECTED","COMPANY",companyId,null,null);
        return status(companyId);
    }

    @PostMapping("/bookings/{bookingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void export(@PathVariable UUID companyId,@PathVariable UUID bookingId) {
        access.requireActiveMembership(companyId);google.requireConfigured();
        limits.acquire("google-export",companyId.toString(),30,60);
        var booking=bookings.findByIdAndCompanyId(bookingId,companyId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,ApiErrorCode.BOOKING_NOT_FOUND,"The booking does not belong to this company"));
        var connections=jdbc.query("SELECT calendar_id,refresh_token_ciphertext FROM google_calendar_connections WHERE company_id=?",
            (row,index)->new String[]{row.getString(1),row.getString(2)},companyId);
        if(connections.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,ApiErrorCode.CALENDAR_AUTHORIZATION_FAILED,"Connect Google Calendar before exporting bookings");
        String resource=jdbc.queryForObject("SELECT name FROM resources WHERE id=? AND company_id=?",String.class,booking.getResourceId(),companyId);
        boolean cancelled=switch(booking.getStatus()) { case CANCELLED,REJECTED,EXPIRED,NO_SHOW -> true; default -> false; };
        Map<String,Object> event=Map.of("id",bookingId.toString().replace("-",""),"summary","Slotix #"+booking.getBookingNumber()+" · "+resource,
            "start",Map.of("dateTime",booking.getStartAt().toString(),"timeZone",booking.getTimezone()),
            "end",Map.of("dateTime",booking.getEndAt().toString(),"timeZone",booking.getTimezone()),
            "status",cancelled?"cancelled":booking.getStatus()==com.slotix.reservationcore.booking.BookingStatus.PENDING?"tentative":"confirmed","visibility","private","transparency",cancelled?"transparent":"opaque",
            "reminders",Map.of("useDefault",false));
        String[] connection=connections.getFirst();
        google.exportBooking(google.accessToken(google.decrypt(companyId,connection[1])),connection[0],bookingId,event);
        audit.record(companyId,access.currentUserId(),"GOOGLE_CALENDAR_BOOKING_EXPORTED","BOOKING",bookingId,null,null);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable UUID companyId) {
        access.requireActiveMembership(companyId);
        jdbc.update("DELETE FROM google_calendar_oauth_states WHERE company_id=?",companyId);
        jdbc.update("DELETE FROM google_calendar_connections WHERE company_id=?",companyId);
        audit.record(companyId,access.currentUserId(),"GOOGLE_CALENDAR_DISCONNECTED","COMPANY",companyId,null,null);
    }
    private boolean connected(UUID companyId) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM google_calendar_connections WHERE company_id=?)",Boolean.class,companyId)); }
    private ApiException conflict() { return new ApiException(HttpStatus.CONFLICT,ApiErrorCode.DATA_CONFLICT,"This company already has a connected calendar"); }
}
