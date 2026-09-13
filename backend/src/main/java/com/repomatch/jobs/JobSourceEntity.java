package com.repomatch.jobs;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A curated GitHub repository whose README lists open jobs. */
@Entity
@Table(name = "job_source")
public class JobSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    private String branch;
    private String path;
    @Enumerated(EnumType.STRING)
    private JobKind defaultKind;
    @Enumerated(EnumType.STRING)
    private SourceType type;
    private String slug;
    private String companyName;
    private boolean enabled;
    private Instant lastCheckedAt;
    private int lastCount;
    private String lastError;

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public JobKind getDefaultKind() { return defaultKind; }
    public void setDefaultKind(JobKind defaultKind) { this.defaultKind = defaultKind; }
    public SourceType getType() { return type; }
    public void setType(SourceType type) { this.type = type; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public int getLastCount() { return lastCount; }
    public void setLastCount(int lastCount) { this.lastCount = lastCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
