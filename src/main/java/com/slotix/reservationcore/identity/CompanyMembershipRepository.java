package com.slotix.reservationcore.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, UUID> {

    Optional<CompanyMembership> findByCompanyIdAndUserIdAndDeletedAtIsNull(
        UUID companyId,
        UUID userId
    );

    Optional<CompanyMembership> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

    Page<CompanyMembership> findByCompanyIdAndDeletedAtIsNull(UUID companyId, Pageable pageable);

    @Query("""
        select count(distinct m.id) from CompanyMembership m join m.roles role
        where m.companyId = :companyId and m.status = 'ACTIVE' and m.deletedAt is null
          and role = com.slotix.reservationcore.identity.MembershipRole.COMPANY_ADMIN
          and m.userId in (select u.id from User u where u.status = 'ACTIVE' and u.deletedAt is null)
        """)
    long countActiveAdministrators(@Param("companyId") UUID companyId);

    boolean existsByCompanyIdAndUserIdAndStatusAndDeletedAtIsNull(
        UUID companyId,
        UUID userId,
        String status
    );
}
