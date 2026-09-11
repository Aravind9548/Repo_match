package com.repomatch.workflow;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {
}
