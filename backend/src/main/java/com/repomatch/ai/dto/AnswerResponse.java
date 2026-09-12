package com.repomatch.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnswerResponse(String llmModel, String answer, List<Citation> citations, boolean answered,
                             ExplainResponse.Usage usage) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Citation(String path, int startLine, int endLine) {
    }
}
