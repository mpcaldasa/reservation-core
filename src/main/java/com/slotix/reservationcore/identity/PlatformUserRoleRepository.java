package com.slotix.reservationcore.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlatformUserRoleRepository extends JpaRepository<PlatformUserRole, PlatformUserRole.PlatformUserRoleId> {

    List<PlatformUserRole> findByUserId(UUID userId);
}
