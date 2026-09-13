package com.repomatch.jobs;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobMatchRepository extends JpaRepository<JobMatchEntity, Long> {
    boolean existsByWatchIdAndPostingId(long watchId, long postingId);

    List<JobMatchEntity> findByWatchIdInOrderByMatchedAtDesc(Collection<Long> watchIds);

    List<JobMatchEntity> findByWatchIdAndEmailedAtIsNull(long watchId);
}
