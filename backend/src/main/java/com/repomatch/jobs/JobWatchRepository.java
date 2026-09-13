package com.repomatch.jobs;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobWatchRepository extends JpaRepository<JobWatchEntity, Long> {
    List<JobWatchEntity> findByOwnerKeyHashOrderByCreatedAtDesc(String ownerKeyHash);

    List<JobWatchEntity> findByActiveTrue();

    Optional<JobWatchEntity> findByIdAndOwnerKeyHash(long id, String ownerKeyHash);
}
