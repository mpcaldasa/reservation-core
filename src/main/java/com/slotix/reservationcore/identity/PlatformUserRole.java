package com.slotix.reservationcore.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "platform_user_roles")
@IdClass(PlatformUserRole.PlatformUserRoleId.class)
public class PlatformUserRole {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private PlatformRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlatformUserRole() {
    }

    public UUID getUserId() {
        return userId;
    }

    public PlatformRole getRole() {
        return role;
    }

    public static class PlatformUserRoleId implements Serializable {
        private UUID userId;
        private PlatformRole role;

        public PlatformUserRoleId() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlatformUserRoleId that)) return false;
            return Objects.equals(userId, that.userId) && role == that.role;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, role);
        }
    }
}
