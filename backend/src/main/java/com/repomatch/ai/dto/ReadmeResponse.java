package com.repomatch.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReadmeResponse(String llmModel, String markdown, int filesUsed, ExplainResponse.Usage usage) {
}
