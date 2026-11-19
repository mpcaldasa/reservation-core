package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoutController {
    private final JwtService jwt;
    private final AccessTokenRevocations revocations;

    public LogoutController(JwtService jwt, AccessTokenRevocations revocations) {
        this.jwt = jwt;
        this.revocations = revocations;
    }

    @PostMapping("/api/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) {
        var claims = jwt.parseAndValidate(authorization.substring(7));
        revocations.revoke(claims.getId(), claims.getExpiration().toInstant());
    }
}
