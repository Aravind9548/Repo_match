package com.repomatch.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExplainRequest(String jobDescription, List<Candidate> repos) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Candidate(long id, String name, String description, List<String> languages,
                            List<String> topics, String readme, double similarity) {
    }
}
