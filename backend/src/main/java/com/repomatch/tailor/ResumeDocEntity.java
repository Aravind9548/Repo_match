package com.repomatch.tailor;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A resume the person gave us: the extracted source text, and its structure once read. */
@Entity
@Table(name = "resume_doc")
public class ResumeDocEntity {

    public static final String TEX = "TEX";
    public static final String TEXT = "TEXT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ownerKeyHash;
    private String filename;
    private String kind;
    private String sourceText;
    /** JSON of the structured resume. */
    private String structured;
    private Instant createdAt;

    public Long getId() { return id; }
    public String getOwnerKeyHash() { return ownerKeyHash; }
    public void setOwnerKeyHash(String ownerKeyHash) { this.ownerKeyHash = ownerKeyHash; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public String getStructured() { return structured; }
    public void setStructured(String structured) { this.structured = structured; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
