package com.repomatch.github.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubRepo(
        long id,
        String name,
        String fullName,
        String description,
        String htmlUrl,
        String homepage,
        String language,
        List<String> topics,
        int stargazersCount,
        boolean fork,
        boolean archived,
        Instant pushedAt,
        Owner owner) {

    public record Owner(String login) {
    }
}
