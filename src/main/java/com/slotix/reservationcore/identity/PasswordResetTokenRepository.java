package com.slotix.reservationcore.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant createdAt);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resetToken from PasswordResetToken resetToken where resetToken.tokenHash = :tokenHash")
    Optional<PasswordResetToken> lockByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update PasswordResetToken resetToken set resetToken.status = 'REVOKED' "
        + "where resetToken.userId = :userId and resetToken.status = 'PENDING'")
    int revokePendingForUser(@Param("userId") UUID userId);
}
