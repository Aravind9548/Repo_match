package com.repomatch.github.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Flattened file listing of a branch. {@code truncated} is set when the repo exceeds GitHub's limit. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubTree(String sha, List<Entry> tree, boolean truncated) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Entry(String path, String type, Long size) {
        public boolean isFile() {
            return "blob".equals(type);
        }
    }
}
