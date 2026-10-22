package com.slotix.reservationcore.identity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyMembershipTest {
    @Test
    void membershipOwnsItsRolesAndRejectsEmptyOrNullAssignments() {
        Set<MembershipRole> roles = new HashSet<>(Set.of(MembershipRole.CUSTOMER));
        CompanyMembership membership = CompanyMembership.create(UUID.randomUUID(), UUID.randomUUID(), roles);
        roles.add(MembershipRole.COMPANY_ADMIN);
        assertEquals(Set.of(MembershipRole.CUSTOMER), membership.getRoles());
        assertThrows(UnsupportedOperationException.class, () -> membership.getRoles().add(MembershipRole.COMPANY_ADMIN));
        assertThrows(IllegalArgumentException.class, () -> membership.replaceRoles(Set.of(), Instant.now()));
        roles.add(null);
        assertThrows(IllegalArgumentException.class, () -> CompanyMembership.create(UUID.randomUUID(), UUID.randomUUID(), roles));
    }

    @Test
    void repeatedChangesPreserveTheirTimestampAndInitialJoinDate() {
        CompanyMembership membership = CompanyMembership.create(UUID.randomUUID(), UUID.randomUUID(), Set.of(MembershipRole.CUSTOMER));
        Instant joinedAt = membership.getJoinedAt();
        Instant changedAt = joinedAt.plusSeconds(1);
        assertTrue(membership.replaceRoles(Set.of(MembershipRole.BOOKING_MANAGER), changedAt));
        assertFalse(membership.replaceRoles(Set.of(MembershipRole.BOOKING_MANAGER), changedAt.plusSeconds(1)));
        assertEquals(changedAt, membership.getUpdatedAt());
        assertTrue(membership.suspend(changedAt.plusSeconds(2)));
        assertFalse(membership.suspend(changedAt.plusSeconds(3)));
        assertTrue(membership.activate(changedAt.plusSeconds(4)));
        assertFalse(membership.activate(changedAt.plusSeconds(5)));
        assertEquals(joinedAt, membership.getJoinedAt());
        assertEquals(changedAt.plusSeconds(4), membership.getUpdatedAt());
    }

    @Test
    void credentialRecordsRedactPasswords() {
        String password = "never-print-this-password";
        assertFalse(new NewUserRequest("user@example.test", password, "User").toString().contains(password));
        assertFalse(new PlatformBootstrapProperties("user@example.test", password, "User").toString().contains(password));
        assertFalse(new PlatformLoginRequest("user@example.test", password).toString().contains(password));
        assertFalse(new LoginRequest(UUID.randomUUID(), "user@example.test", password).toString().contains(password));
        assertFalse(new RegisterUserRequest("user@example.test", password, "User", UUID.randomUUID(),
            Set.of(MembershipRole.CUSTOMER)).toString().contains(password));
    }
}
