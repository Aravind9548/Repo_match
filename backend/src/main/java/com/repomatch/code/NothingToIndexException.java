package com.repomatch.code;

public class NothingToIndexException extends RuntimeException {
    public NothingToIndexException(String repoName) {
        super("'" + repoName + "' has no readable source files to index");
    }
}
