package com.slotix.reservationcore.identity;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final CompanyMembershipService memberships;
    private final IdentityAuthenticationService authentication;

    public UserController(CompanyMembershipService memberships, IdentityAuthenticationService authentication) {
        this.memberships = memberships;
        this.authentication = authentication;
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.ok(memberships.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authentication.login(request));
    }
}
