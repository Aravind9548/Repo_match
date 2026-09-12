package com.repomatch.code;

/** Asked a question about a repository whose code hasn't been read yet. */
public class NotIndexedException extends RuntimeException {
    public NotIndexedException(String repoName) {
        super("'" + repoName + "' hasn't been indexed yet — index it before asking questions");
    }
}
