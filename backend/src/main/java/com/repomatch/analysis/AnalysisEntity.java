package com.repomatch.analysis;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis")
public class AnalysisEntity {

    @Id
    private UUID id;
    private String username;
    private String jobDescription;
    @Column(name = "top_k")
    private int topK;
    private String embeddingModel;
    private String llmModel;

    @JdbcTypeCode(SqlTypes.JSON)
    private AnalysisResults results;

    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public AnalysisResults getResults() { return results; }
    public void setResults(AnalysisResults results) { this.results = results; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
