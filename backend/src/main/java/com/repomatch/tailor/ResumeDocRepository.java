package com.repomatch.tailor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeDocRepository extends JpaRepository<ResumeDocEntity, Long> {

    List<ResumeDocEntity> findByOwnerKeyHashOrderByCreatedAtDesc(String ownerKeyHash);

    Optional<ResumeDocEntity> findByIdAndOwnerKeyHash(long id, String ownerKeyHash);
}
