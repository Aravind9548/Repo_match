package com.repomatch.repo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the text "profile" of a repository — the single document that gets embedded
 * and compared against a job description.
 */
public final class ProfileBuilder {

    /** Embedding models truncate long inputs anyway; keep the most informative head of the README. */
    static final int MAX_README_IN_PROFILE = 6000;

    private ProfileBuilder() {
    }

    public static String build(RepoEntity repo) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(repo.getName()).append('\n');
        if (hasText(repo.getDescription())) {
            sb.append("Description: ").append(repo.getDescription().strip()).append('\n');
        }
        if (!repo.getTopics().isEmpty()) {
            sb.append("Topics: ").append(String.join(", ", repo.getTopics())).append('\n');
        }
        String languages = formatLanguages(repo.getLanguages());
        if (!languages.isEmpty()) {
            sb.append("Languages: ").append(languages).append('\n');
        }
        if (hasText(repo.getReadme())) {
            String readme = repo.getReadme();
            if (readme.length() > MAX_README_IN_PROFILE) {
                readme = readme.substring(0, MAX_README_IN_PROFILE);
            }
            sb.append("README:\n").append(readme).append('\n');
        }
        return sb.toString().strip();
    }

    /** "Java 72%, TypeScript 25%, Dockerfile 3%" — languages under 1% are dropped. */
    static String formatLanguages(Map<String, Long> languages) {
        long total = languages.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return String.join(", ", languages.keySet());
        }
        return languages.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .filter(e -> e.getValue() * 100 / total >= 1)
                .map(e -> e.getKey() + " " + Math.round(e.getValue() * 100.0 / total) + "%")
                .collect(Collectors.joining(", "));
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
