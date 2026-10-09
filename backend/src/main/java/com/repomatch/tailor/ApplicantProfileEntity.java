package com.repomatch.tailor;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The answers every application asks for, kept once so they can be filled in seconds. */
@Entity
@Table(name = "applicant_profile")
public class ApplicantProfileEntity {

    @Id
    private String ownerKeyHash;
    private String fullName;
    private String email;
    private String phone;
    private String location;
    private String githubUrl;
    private String linkedinUrl;
    private String portfolioUrl;
    private String workAuthorization;
    private String noticePeriod;
    private String expectedSalary;
    private String graduation;
    /** JSON list of anything else to autofill: [{"label":"Gender","value":"..."}]. */
    private String extras;
    private Instant updatedAt;

    public String getOwnerKeyHash() { return ownerKeyHash; }
    public void setOwnerKeyHash(String ownerKeyHash) { this.ownerKeyHash = ownerKeyHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }
    public String getWorkAuthorization() { return workAuthorization; }
    public void setWorkAuthorization(String workAuthorization) { this.workAuthorization = workAuthorization; }
    public String getNoticePeriod() { return noticePeriod; }
    public void setNoticePeriod(String noticePeriod) { this.noticePeriod = noticePeriod; }
    public String getExpectedSalary() { return expectedSalary; }
    public void setExpectedSalary(String expectedSalary) { this.expectedSalary = expectedSalary; }
    public String getGraduation() { return graduation; }
    public void setGraduation(String graduation) { this.graduation = graduation; }
    public String getExtras() { return extras; }
    public void setExtras(String extras) { this.extras = extras; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
