package com.repomatch.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "repomatch")
public record AppProperties(GitHub github, Ai ai, Analysis analysis, Limits limits, Cors cors) {

    /** Guards on paid LLM calls (see UsageGuard). */
    public record Limits(int llmCallsPerClientPerHour, int llmCallsPerDay) {
    }

    public record GitHub(String baseUrl, String token, Duration cacheTtl, int maxReadmeChars, int maxParallelRequests) {
        public boolean hasToken() {
            return token != null && !token.isBlank();
        }
    }

    /** @param audience set on Cloud Run: calls carry an ID token for this audience (the AI service URL). */
    public record Ai(String baseUrl, Duration connectTimeout, Duration readTimeout, String audience) {
        public boolean requiresIdToken() {
            return audience != null && !audience.isBlank();
        }
    }

    /**
     * @param rerankPoolExtra extra candidates the LLM judges beyond top-k
     * @param rerankPoolMin   the LLM always judges at least this many candidates (when available)
     * @param minReadmeChars  repos with a shorter README only fill the LLM pool after content-rich ones
     */
    public record Analysis(int defaultTopK, int maxTopK, int rerankPoolExtra, int rerankPoolMin, int minReadmeChars) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
