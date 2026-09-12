package com.repomatch.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnswerRequest(String repo, String question, List<Chunk> chunks) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Chunk(String path, int startLine, int endLine, String text) {
    }
}
