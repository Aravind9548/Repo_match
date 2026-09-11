package com.repomatch.analysis;

public class NoRepositoriesException extends RuntimeException {
    public NoRepositoriesException(String login) {
        super("GitHub user '" + login + "' has no public repositories to analyze");
    }
}
