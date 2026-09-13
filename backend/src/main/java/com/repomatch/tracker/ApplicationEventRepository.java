package com.repomatch.tracker;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEventEntity, Long> {

    List<ApplicationEventEntity> findByApplicationIdOrderByOccurredAtDescIdDesc(long applicationId);
}
