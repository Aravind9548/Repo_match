package com.repomatch.analysis;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$", message = "must be a valid GitHub username")
        String username,

        @NotBlank
        @Size(min = 50, max = 20000, message = "must be between 50 and 20000 characters")
        String jobDescription,

        @Min(1) @Max(10)
        Integer topK,

        /** Include forked repos in the ranking (off by default: forks are mostly other people's work). */
        Boolean includeForks,

        /** Bypass the account cache and re-sync from GitHub. */
        Boolean refresh) {
}
