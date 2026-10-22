package com.slotix.reservationcore.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, UUID> {
    Optional<CompanyInvitation> findByTokenHash(String tokenHash);

    boolean existsByCompanyIdAndInvitedEmailAndStatus(UUID companyId, String invitedEmail, String status);

    boolean existsByCompanyIdAndInvitedEmailAndStatusAndExpiresAtAfter(UUID companyId, String invitedEmail,
                                                                         String status, java.time.Instant now);

    @Modifying
    @Query("update CompanyInvitation invitation set invitation.status = 'EXPIRED' where invitation.companyId = :companyId "
        + "and invitation.invitedEmail = :email and invitation.status = 'PENDING' and invitation.expiresAt <= :now")
    int expirePending(@Param("companyId") UUID companyId, @Param("email") String email,
                      @Param("now") java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from CompanyInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<CompanyInvitation> lockByTokenHash(@Param("tokenHash") String tokenHash);
}
