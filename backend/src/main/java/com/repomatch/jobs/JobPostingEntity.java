package com.repomatch.jobs;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_posting")
public class JobPostingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long sourceId;
    private String company;
    private String role;
    private String location;
    @Enumerated(EnumType.STRING)
    private JobKind kind;
    private String applyUrl;
    private boolean closed;
    private String ageLabel;
    private String country;
    private boolean remote;
    private Instant postedAt;
    private String searchText;
    private Instant firstSeenAt;
    private Instant lastSeenAt;

    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public JobKind getKind() { return kind; }
    public void setKind(JobKind kind) { this.kind = kind; }
    public String getApplyUrl() { return applyUrl; }
    public void setApplyUrl(String applyUrl) { this.applyUrl = applyUrl; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public String getAgeLabel() { return ageLabel; }
    public void setAgeLabel(String ageLabel) { this.ageLabel = ageLabel; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public boolean isRemote() { return remote; }
    public void setRemote(boolean remote) { this.remote = remote; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }
    /** When the employer posted it if known, else when we first saw it. */
    public Instant getListedAt() { return postedAt != null ? postedAt : firstSeenAt; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
