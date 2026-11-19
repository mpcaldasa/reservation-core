package com.slotix.reservationcore.identity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformAuthenticationController {
    private final IdentityAuthenticationService authentication;

    public PlatformAuthenticationController(IdentityAuthenticationService authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody PlatformLoginRequest request) {
        return authentication.platformLogin(request);
    }
}
