package com.repomatch.analysis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.repomatch.config.AppProperties;

/**
 * Caps paid LLM calls so a public deployment can't drain the Gemini budget:
 * a sliding one-hour limit per client plus a global daily limit (UTC).
 * In-memory, so it assumes a single backend instance and resets on restart.
 */
@Component
public class UsageGuard {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int perClientPerHour;
    private final int perDay;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recentByClient = new HashMap<>();
    private LocalDate day;
    private int usedToday;

    @Autowired
    public UsageGuard(AppProperties props) {
        this(props.limits(), Clock.systemUTC());
    }

    UsageGuard(AppProperties.Limits limits, Clock clock) {
        this.perClientPerHour = limits.llmCallsPerClientPerHour();
        this.perDay = limits.llmCallsPerDay();
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    /** Records one LLM call for this client, or throws if a limit is reached. */
    public synchronized void acquireLlmCall(String clientKey) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!today.equals(day)) {
            day = today;
            usedToday = 0;
        }
        if (usedToday >= perDay) {
            Instant midnight = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            throw new UsageLimitException("RepoMatch has reached its daily analysis limit. Please try again tomorrow.",
                    Duration.between(now, midnight));
        }

        Deque<Instant> recent = recentByClient.computeIfAbsent(clientKey, k -> new ArrayDeque<>());
        Instant cutoff = now.minus(WINDOW);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.pollFirst();
        }
        if (recent.size() >= perClientPerHour) {
            throw new UsageLimitException("You've run " + perClientPerHour
                    + " analyses in the last hour. Please wait a little before the next one.",
                    Duration.between(now, recent.peekFirst().plus(WINDOW)));
        }
        recent.addLast(now);
        usedToday++;
    }
}
