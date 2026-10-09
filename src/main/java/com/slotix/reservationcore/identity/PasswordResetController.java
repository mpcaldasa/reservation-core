package com.slotix.reservationcore.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/password-resets")
public class PasswordResetController {
    private final PasswordResetService passwordResets;

    public PasswordResetController(PasswordResetService passwordResets) { this.passwordResets = passwordResets; }

    @PostMapping
    public ResponseEntity<PasswordResetResponse> request(@Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(passwordResets.request(request));
    }

    @PostMapping("/confirm")
    public PasswordResetResponse confirm(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        return passwordResets.confirm(request);
    }
}
