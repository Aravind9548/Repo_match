package com.repomatch.github;

public class GitHubUserNotFoundException extends GitHubException {
    public GitHubUserNotFoundException(String login) {
        super("GitHub user '" + login + "' was not found");
    }
}
