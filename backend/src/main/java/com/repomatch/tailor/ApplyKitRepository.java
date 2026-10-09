package com.repomatch.tailor;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplyKitRepository extends JpaRepository<ApplyKitEntity, Long> {

    Optional<ApplyKitEntity> findByApplicationIdAndOwnerKeyHash(long applicationId, String ownerKeyHash);
}
