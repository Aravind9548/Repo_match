package com.repomatch.github.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubUser(String login, String name, String avatarUrl, String htmlUrl, int publicRepos) {
}
