package com.repomatch.tracker;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    List<ApplicationEntity> findByOwnerKeyHashOrderByUpdatedAtDesc(String ownerKeyHash);

    Optional<ApplicationEntity> findByIdAndOwnerKeyHash(long id, String ownerKeyHash);

    Optional<ApplicationEntity> findByOwnerKeyHashAndPostingId(String ownerKeyHash, long postingId);

    /** Deadlines coming up, or applications that went quiet, that haven't been reminded about recently. */
    @Query("""
            select a from ApplicationEntity a
            where (a.remindedAt is null or a.remindedAt < :remindedBefore)
              and a.status not in (com.repomatch.tracker.ApplicationStatus.OFFER, com.repomatch.tracker.ApplicationStatus.REJECTED,
                                   com.repomatch.tracker.ApplicationStatus.GHOSTED, com.repomatch.tracker.ApplicationStatus.WITHDRAWN)
              and ((a.nextStepDue is not null and a.nextStepDue <= :dueBy)
                   or (a.status = com.repomatch.tracker.ApplicationStatus.APPLIED and a.updatedAt < :quietSince)
                   or (a.status = com.repomatch.tracker.ApplicationStatus.APPROVED and a.createdAt < :staleApprovalSince))
            """)
    List<ApplicationEntity> findNeedingReminder(@Param("remindedBefore") Instant remindedBefore,
                                                @Param("dueBy") LocalDate dueBy,
                                                @Param("quietSince") Instant quietSince,
                                                @Param("staleApprovalSince") Instant staleApprovalSince);
}
