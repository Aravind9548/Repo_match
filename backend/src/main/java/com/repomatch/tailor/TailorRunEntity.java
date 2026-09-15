package com.repomatch.tailor;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One tailoring of one resume for one job, gate by gate. JSON fields are stored as text. */
@Entity
@Table(name = "tailor_run")
public class TailorRunEntity {

    public enum Step { REVIEW_RESUME, REVIEW_EDITS, DONE }

    @Id
    private UUID id;
    private String ownerKeyHash;
    private Long applicationId;
    private Long postingId;
    private Long resumeId;
    @Enumerated(EnumType.STRING)
    private Step step;
    private String company;
    private String jobTitle;
    private String jobDescription;
    private String structured;
    private String plan;
    private String acceptedIds;
    private String latex;
    private byte[] pdf;
    private boolean compiled;
    private String compileLog;
    private boolean usedOwnTemplate;
    private String missedEdits;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerKeyHash() { return ownerKeyHash; }
    public void setOwnerKeyHash(String ownerKeyHash) { this.ownerKeyHash = ownerKeyHash; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getPostingId() { return postingId; }
    public void setPostingId(Long postingId) { this.postingId = postingId; }
    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }
    public Step getStep() { return step; }
    public void setStep(Step step) { this.step = step; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public String getStructured() { return structured; }
    public void setStructured(String structured) { this.structured = structured; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public String getAcceptedIds() { return acceptedIds; }
    public void setAcceptedIds(String acceptedIds) { this.acceptedIds = acceptedIds; }
    public String getLatex() { return latex; }
    public void setLatex(String latex) { this.latex = latex; }
    public byte[] getPdf() { return pdf; }
    public void setPdf(byte[] pdf) { this.pdf = pdf; }
    public boolean isCompiled() { return compiled; }
    public void setCompiled(boolean compiled) { this.compiled = compiled; }
    public String getCompileLog() { return compileLog; }
    public void setCompileLog(String compileLog) { this.compileLog = compileLog; }
    public boolean isUsedOwnTemplate() { return usedOwnTemplate; }
    public void setUsedOwnTemplate(boolean usedOwnTemplate) { this.usedOwnTemplate = usedOwnTemplate; }
    public String getMissedEdits() { return missedEdits; }
    public void setMissedEdits(String missedEdits) { this.missedEdits = missedEdits; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
}
