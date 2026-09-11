package com.repomatch.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RankResponse(String embeddingModel, List<Ranked> results) {

    /** {@code embedding} is only present for repos the AI service had to (re)embed. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Ranked(long id, double similarity, float[] embedding) {
    }
}
