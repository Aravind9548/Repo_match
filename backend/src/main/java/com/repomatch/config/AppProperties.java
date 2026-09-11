package com.repomatch.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "repomatch")
public record AppProperties(GitHub github, Ai ai, Analysis analysis, Cors cors) {

    public record GitHub(String baseUrl, String token, Duration cacheTtl, int maxReadmeChars, int maxParallelRequests) {
        public boolean hasToken() {
            return token != null && !token.isBlank();
        }
    }

    public record Ai(String baseUrl, Duration connectTimeout, Duration readTimeout) {
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
