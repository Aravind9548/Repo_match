package com.repomatch.workflow;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "workflow")
public class WorkflowEntity {

    @Id
    private UUID id;
    private String username;
    private String jobDescription;
    @Column(name = "top_k")
    private int topK;
    private boolean includeForks;

    @Enumerated(EnumType.STRING)
    private WorkflowStep step;

    @JdbcTypeCode(SqlTypes.JSON)
    private WorkflowState state;

    private UUID analysisId;

    /** Optimistic locking: two tabs approving the same step can't both win. */
    @Version
    private long version;

    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public boolean isIncludeForks() { return includeForks; }
    public void setIncludeForks(boolean includeForks) { this.includeForks = includeForks; }
    public WorkflowStep getStep() { return step; }
    public void setStep(WorkflowStep step) { this.step = step; }
    public WorkflowState getState() { return state; }
    public void setState(WorkflowState state) { this.state = state; }
    public UUID getAnalysisId() { return analysisId; }
    public void setAnalysisId(UUID analysisId) { this.analysisId = analysisId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
