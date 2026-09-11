package com.repomatch.github;

import java.time.Instant;

public class GitHubRateLimitException extends GitHubException {

    private final Instant resetAt;

    public GitHubRateLimitException(Instant resetAt) {
        super("GitHub API rate limit exceeded" + (resetAt != null ? "; resets at " + resetAt : ""));
        this.resetAt = resetAt;
    }

    public Instant getResetAt() {
        return resetAt;
    }
}
