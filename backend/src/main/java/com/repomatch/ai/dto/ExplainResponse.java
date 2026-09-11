package com.repomatch.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExplainResponse(String llmModel, List<Explanation> results, Usage usage) {

    /** Token counts reported by Gemini for the explain call — what it cost. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Usage(int promptTokens, int outputTokens, int thinkingTokens) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Explanation(long id, int fitScore, String reason, List<String> matchedRequirements,
                              List<String> evidence) {
    }
}
