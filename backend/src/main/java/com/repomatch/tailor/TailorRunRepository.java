package com.repomatch.tailor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TailorRunRepository extends JpaRepository<TailorRunEntity, UUID> {

    Optional<TailorRunEntity> findByIdAndOwnerKeyHash(UUID id, String ownerKeyHash);

    List<TailorRunEntity> findByOwnerKeyHashAndApplicationIdOrderByUpdatedAtDesc(String ownerKeyHash, long applicationId);

    List<TailorRunEntity> findByOwnerKeyHash(String ownerKeyHash);
}
