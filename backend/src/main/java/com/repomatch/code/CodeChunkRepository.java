package com.repomatch.code;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface CodeChunkRepository extends JpaRepository<CodeChunkEntity, Long> {

    List<CodeChunkEntity> findByRepoId(Long repoId);

    @Modifying
    @Transactional
    @Query("delete from CodeChunkEntity c where c.repoId = :repoId")
    void deleteByRepoId(Long repoId);
}
