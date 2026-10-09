package com.repomatch.tailor;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The drafted answers for one application, built from the resume, the repos and the profile. */
@Entity
@Table(name = "apply_kit")
public class ApplyKitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ownerKeyHash;
    private Long applicationId;
    private UUID tailorRunId;
    /** JSON: [{"question":..., "answer":..., "grounded_on":..., "unsupported":...}] */
    private String answers;
    private String model;
    private Instant createdAt;

    public Long getId() { return id; }
    public String getOwnerKeyHash() { return ownerKeyHash; }
    public void setOwnerKeyHash(String ownerKeyHash) { this.ownerKeyHash = ownerKeyHash; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public UUID getTailorRunId() { return tailorRunId; }
    public void setTailorRunId(UUID tailorRunId) { this.tailorRunId = tailorRunId; }
    public String getAnswers() { return answers; }
    public void setAnswers(String answers) { this.answers = answers; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
