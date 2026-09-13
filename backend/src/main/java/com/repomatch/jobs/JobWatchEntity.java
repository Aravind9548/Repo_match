package com.repomatch.jobs;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A saved search: "tell me when a posting like this shows up". */
@Entity
@Table(name = "job_watch")
public class JobWatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ownerKeyHash;
    private String name;
    private String email;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] keywords = new String[0];
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] excludeKeywords = new String[0];
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] locations = new String[0];
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] kinds = new String[0];
    private boolean active = true;
    private Instant createdAt;
    private Instant lastNotifiedAt;

    /**
     * Keywords match the role or company (any one is enough), excluded words veto, locations match any substring,
     * and "remote" in the locations list also matches postings that say remote anywhere.
     */
    public boolean matches(JobPostingEntity p) {
        if (p.isClosed()) {
            return false;
        }
        String haystack = (p.getRole() + " " + p.getCompany()).toLowerCase(Locale.ROOT);
        String where = p.getLocation() == null ? "" : p.getLocation().toLowerCase(Locale.ROOT);
        if (kinds.length > 0 && Arrays.stream(kinds).noneMatch(k -> k.equals(p.getKind().name()))) {
            return false;
        }
        if (Arrays.stream(excludeKeywords).anyMatch(k -> haystack.contains(lower(k)))) {
            return false;
        }
        if (keywords.length > 0 && Arrays.stream(keywords).noneMatch(k -> haystack.contains(lower(k)))) {
            return false;
        }
        return locations.length == 0 || Arrays.stream(locations).anyMatch(l -> where.contains(lower(l)));
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT).strip();
    }

    public Long getId() { return id; }
    public String getOwnerKeyHash() { return ownerKeyHash; }
    public void setOwnerKeyHash(String ownerKeyHash) { this.ownerKeyHash = ownerKeyHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String[] getKeywords() { return keywords; }
    public void setKeywords(String[] keywords) { this.keywords = keywords; }
    public String[] getExcludeKeywords() { return excludeKeywords; }
    public void setExcludeKeywords(String[] excludeKeywords) { this.excludeKeywords = excludeKeywords; }
    public String[] getLocations() { return locations; }
    public void setLocations(String[] locations) { this.locations = locations; }
    public String[] getKinds() { return kinds; }
    public void setKinds(Set<JobKind> kinds) { this.kinds = kinds.stream().map(Enum::name).sorted().toArray(String[]::new); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(Instant lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }
}
