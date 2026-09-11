package com.repomatch.analysis;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<AnalysisEntity, UUID> {

    List<AnalysisEntity> findTop20ByUsernameOrderByCreatedAtDesc(String username);
}
