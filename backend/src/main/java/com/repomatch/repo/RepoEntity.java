package com.repomatch.repo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A GitHub repository plus the text profile and embedding we derive from it. */
@Entity
@Table(name = "repo")
public class RepoEntity {

    /** GitHub's own repository id. */
    @Id
    private Long id;
    private String ownerLogin;
    private String name;
    private String fullName;
    private String description;
    private String htmlUrl;
    private String homepage;
    private String primaryLanguage;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Long> languages = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> topics = new ArrayList<>();

    private int stars;
    private boolean fork;
    private boolean archived;
    private String defaultBranch;
    private Instant pushedAt;
    private String readme;
    private String profileText;
    private String profileHash;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "real[]")
    private float[] embedding;
    private String embeddingModel;
    private String embeddingProfileHash;

    private Instant fetchedAt;

    /** True when the stored embedding was computed from the current profile text. */
    public boolean hasFreshEmbedding() {
        return embedding != null && profileHash != null && profileHash.equals(embeddingProfileHash);
    }

    /** Language names ordered by bytes of code, largest first. */
    public List<String> languageNames() {
        return languages.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOwnerLogin() { return ownerLogin; }
    public void setOwnerLogin(String ownerLogin) { this.ownerLogin = ownerLogin; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public String getHomepage() { return homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }
    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }
    public Map<String, Long> getLanguages() { return languages; }
    public void setLanguages(Map<String, Long> languages) { this.languages = languages; }
    public List<String> getTopics() { return topics; }
    public void setTopics(List<String> topics) { this.topics = topics; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public boolean isFork() { return fork; }
    public void setFork(boolean fork) { this.fork = fork; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public Instant getPushedAt() { return pushedAt; }
    public void setPushedAt(Instant pushedAt) { this.pushedAt = pushedAt; }
    public String getReadme() { return readme; }
    public void setReadme(String readme) { this.readme = readme; }
    public String getProfileText() { return profileText; }
    public void setProfileText(String profileText) { this.profileText = profileText; }
    public String getProfileHash() { return profileHash; }
    public void setProfileHash(String profileHash) { this.profileHash = profileHash; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getEmbeddingProfileHash() { return embeddingProfileHash; }
    public void setEmbeddingProfileHash(String embeddingProfileHash) { this.embeddingProfileHash = embeddingProfileHash; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
