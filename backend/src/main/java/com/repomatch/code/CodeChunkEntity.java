package com.repomatch.code;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One embedded slice of a source file, with the line range it came from. */
@Entity
@Table(name = "code_chunk")
public class CodeChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long repoId;
    private String path;
    private int startLine;
    private int endLine;
    private String content;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "real[]")
    private float[] embedding;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRepoId() { return repoId; }
    public void setRepoId(Long repoId) { this.repoId = repoId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }
    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
}
