package com.repomatch.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WebSearchResponse(String model, List<Job> jobs, List<String> searchQueries, ExplainResponse.Usage usage) {

    public record Job(String title, String company, String location, String url, String posted) {
    }
}
