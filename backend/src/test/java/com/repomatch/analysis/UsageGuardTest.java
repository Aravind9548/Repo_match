package com.repomatch.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.repomatch.config.AppProperties;

class UsageGuardTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-09-11T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();

    @Test
    void limitsEachClientPerHourWithSlidingWindow() {
        UsageGuard guard = new UsageGuard(new AppProperties.Limits(2, 100), clock);
        guard.acquireLlmCall("1.1.1.1");
        clock.now = clock.now.plus(Duration.ofMinutes(10));
        guard.acquireLlmCall("1.1.1.1");

        assertThatThrownBy(() -> guard.acquireLlmCall("1.1.1.1"))
                .isInstanceOf(UsageLimitException.class)
                .satisfies(e -> assertThat(((UsageLimitException) e).getRetryAfter()).isEqualTo(Duration.ofMinutes(50)));
        guard.acquireLlmCall("2.2.2.2"); // other clients are unaffected

        clock.now = clock.now.plus(Duration.ofMinutes(50)); // first call leaves the window
        guard.acquireLlmCall("1.1.1.1");
    }

    @Test
    void enforcesGlobalDailyLimitAndResetsAtUtcMidnight() {
        UsageGuard guard = new UsageGuard(new AppProperties.Limits(10, 2), clock);
        guard.acquireLlmCall("a");
        guard.acquireLlmCall("b");
        assertThatThrownBy(() -> guard.acquireLlmCall("c")).isInstanceOf(UsageLimitException.class);

        clock.now = Instant.parse("2026-09-12T00:00:01Z");
        guard.acquireLlmCall("c");
    }
}
