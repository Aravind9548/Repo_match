package com.repomatch.repo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubAccountRepository extends JpaRepository<GithubAccount, String> {
}
