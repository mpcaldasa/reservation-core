package com.slotix.reservationcore.availability;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceBlockRepository extends JpaRepository<ResourceBlock, UUID> {
    List<ResourceBlock> findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByStartAtAsc(UUID companyId, UUID resourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select block from ResourceBlock block where block.id = :blockId and block.companyId = :companyId "
        + "and block.resourceId = :resourceId")
    Optional<ResourceBlock> lockByIdAndCompanyAndResource(@Param("blockId") UUID blockId,
        @Param("companyId") UUID companyId, @Param("resourceId") UUID resourceId);
}
