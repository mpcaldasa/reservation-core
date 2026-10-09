package com.slotix.reservationcore.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PlatformUserRoleRepository extends JpaRepository<PlatformUserRole, PlatformUserRole.PlatformUserRoleId> {

    List<PlatformUserRole> findByUserId(UUID userId);
    boolean existsByRole(PlatformRole role);
    boolean existsByUserIdAndRole(UUID userId, PlatformRole role);

    @Query(value = "select count(*) from platform_user_roles role_assignment "
        + "join users account on account.id = role_assignment.user_id "
        + "where role_assignment.role = 'PLATFORM_ADMIN' and account.status = 'ACTIVE' and account.deleted_at is null",
        nativeQuery = true)
    long countActivePlatformAdministrators();

    List<PlatformUserRole> findByUserIdIn(java.util.Collection<UUID> userIds);
}
