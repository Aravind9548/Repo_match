package com.repomatch.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RankRequest(String jobDescription, List<Candidate> repos) {

    /** A repo profile; {@code embedding} is sent when we already have a fresh cached one. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Candidate(long id, String text, float[] embedding, String embeddingModel) {
    }
}
