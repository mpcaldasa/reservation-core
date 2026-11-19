package com.slotix.reservationcore.common;

import com.slotix.reservationcore.identity.IdentityAuthenticationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final IdentityAuthenticationService identities;
    private final com.slotix.reservationcore.identity.AccessTokenRevocations revocations;

    public JwtAuthenticationFilter(JwtService jwt, IdentityAuthenticationService identities,
                                    com.slotix.reservationcore.identity.AccessTokenRevocations revocations) {
        this.jwt = jwt;
        this.identities = identities;
        this.revocations = revocations;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            SecurityContextHolder.clearContext();
            AuthenticatedPrincipal principal = parsePrincipal(header.substring(7));
            if (principal != null) {
                identities.resolve(principal.userId(), principal.companyId(), principal.credentialVersion()).ifPresent(identity -> {
                    var authorities = identity.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                    var authentication = new UsernamePasswordAuthenticationToken(identity.principal(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }
        chain.doFilter(request, response);
    }

    private AuthenticatedPrincipal parsePrincipal(String token) {
        try {
            Claims claims = jwt.parseAndValidate(token);
            if (claims.getSubject() == null || claims.getExpiration() == null || revocations.isRevoked(claims.getId())) {
                return null;
            }
            String companyId = claims.get("companyId", String.class);
            Integer credentialVersion = claims.get("credentialVersion", Integer.class);
            return new AuthenticatedPrincipal(UUID.fromString(claims.getSubject()),
                companyId == null ? null : UUID.fromString(companyId), credentialVersion == null ? 0 : credentialVersion);
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }
}
