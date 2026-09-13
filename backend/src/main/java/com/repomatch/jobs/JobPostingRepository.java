package com.repomatch.jobs;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobPostingRepository extends JpaRepository<JobPostingEntity, Long> {
    List<JobPostingEntity> findByApplyUrlIn(Collection<String> urls);

    /** Open postings listed since a moment: the employer's date when known, else when we first saw it. */
    @Query("select p from JobPostingEntity p where p.closed = false and coalesce(p.postedAt, p.firstSeenAt) >= :since")
    List<JobPostingEntity> findOpenListedSince(@Param("since") Instant since);

    /** Anything a list stopped showing is gone: closed or filled. */
    @Modifying
    @Query("update JobPostingEntity p set p.closed = true where p.sourceId = :sourceId and p.lastSeenAt < :seenBefore and p.closed = false")
    int closeMissing(@Param("sourceId") long sourceId, @Param("seenBefore") Instant seenBefore);

    @Query("select p from JobPostingEntity p where p.closed = false order by p.firstSeenAt desc, p.id desc")
    List<JobPostingEntity> findOpen();
}
