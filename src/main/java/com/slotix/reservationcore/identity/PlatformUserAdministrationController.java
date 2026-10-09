package com.slotix.reservationcore.identity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/platform/users")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformUserAdministrationController {
    private final PlatformUserAdministrationService users;

    public PlatformUserAdministrationController(PlatformUserAdministrationService users) { this.users = users; }

    @GetMapping
    public PlatformUserPageResponse list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return users.list(page, size);
    }

    @PostMapping("/{userId}/deactivate")
    public PlatformUserResponse deactivate(@PathVariable UUID userId) { return users.deactivate(userId); }

    @PostMapping("/{userId}/reactivate")
    public PlatformUserResponse reactivate(@PathVariable UUID userId) { return users.reactivate(userId); }

    @PostMapping("/{userId}/platform-admin")
    public PlatformUserResponse grantPlatformAdmin(@PathVariable UUID userId) {
        return users.grantPlatformAdmin(userId);
    }

    @DeleteMapping("/{userId}/platform-admin")
    public PlatformUserResponse revokePlatformAdmin(@PathVariable UUID userId) {
        return users.revokePlatformAdmin(userId);
    }
}
