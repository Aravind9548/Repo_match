package com.repomatch.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepoRepository extends JpaRepository<RepoEntity, Long> {

    List<RepoEntity> findByOwnerLoginOrderByPushedAtDesc(String ownerLogin);
}
