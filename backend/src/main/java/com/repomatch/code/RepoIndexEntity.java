package com.repomatch.code;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bookkeeping for one indexed repository: what was read, when, and with which model. */
@Entity
@Table(name = "repo_index")
public class RepoIndexEntity {

    @Id
    private Long repoId;
    private String commitSha;
    private int fileCount;
    private int chunkCount;
    private int skippedFiles;
    private String model;
    private Instant indexedAt;

    public Long getRepoId() { return repoId; }
    public void setRepoId(Long repoId) { this.repoId = repoId; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public int getSkippedFiles() { return skippedFiles; }
    public void setSkippedFiles(int skippedFiles) { this.skippedFiles = skippedFiles; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
}
