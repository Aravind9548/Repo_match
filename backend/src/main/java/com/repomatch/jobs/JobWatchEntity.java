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
     * Uses the feed's own search rules: each keyword is a search in its own right ("sde" also finds "software
     * engineer") and any one is enough; excluded words veto; each location is a country, city (with its other
     * spellings) or "remote", and any one is enough.
     */
    public boolean matches(JobPostingEntity p) {
        if (p.isClosed()) {
            return false;
        }
        String text = p.getSearchText() == null || p.getSearchText().isBlank()
                ? SearchText.build(p.getCompany(), p.getRole(), p.getLocation(), Places.of(p.getLocation()))
                : p.getSearchText();
        if (kinds.length > 0 && Arrays.stream(kinds).noneMatch(k -> k.equals(p.getKind().name()))) {
            return false;
        }
        if (Arrays.stream(excludeKeywords).anyMatch(k -> SearchText.matches(text, java.util.List.of(k)))) {
            return false;
        }
        if (keywords.length > 0 && Arrays.stream(keywords).noneMatch(k -> keywordMatches(text, k))) {
            return false;
        }
        return locations.length == 0 || Arrays.stream(locations).anyMatch(l -> locationMatches(p, text, l));
    }

    /** "backend java" as one keyword means both words; synonyms apply to each. */
    private static boolean keywordMatches(String text, String keyword) {
        JobQuery q = JobQuery.parse(keyword, null);
        if (q.concepts().isEmpty()) {
            return true; // the keyword was only a kind or place word, which the other filters cover
        }
        return q.concepts().stream().allMatch(c -> SearchText.matches(text, c));
    }

    private static boolean locationMatches(JobPostingEntity p, String text, String location) {
        Places.Wanted w = Places.wanted(location);
        if (w == null) {
            return SearchText.matches(text, java.util.List.of(location));
        }
        if (w.remote()) {
            return p.isRemote();
        }
        if (w.country() != null && w.cityAliases().isEmpty()) {
            return w.country().equals(p.getCountry());
        }
        return SearchText.matches(text, w.cityAliases());
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
