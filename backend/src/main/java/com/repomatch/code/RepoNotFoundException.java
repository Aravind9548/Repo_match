package com.repomatch.code;

public class RepoNotFoundException extends RuntimeException {
    public RepoNotFoundException(long repoId) {
        super("Repository " + repoId + " isn't synced — fetch the account's repositories first");
    }
}
