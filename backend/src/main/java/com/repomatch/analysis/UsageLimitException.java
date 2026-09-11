package com.repomatch.analysis;

import java.time.Duration;

public class UsageLimitException extends RuntimeException {

    private final Duration retryAfter;

    public UsageLimitException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
