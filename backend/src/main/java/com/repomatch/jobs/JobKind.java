package com.repomatch.jobs;

import java.util.Locale;
import java.util.regex.Pattern;

public enum JobKind {
    INTERNSHIP, NEW_GRAD, FULL_TIME;

    private static final Pattern INTERN_TITLE = Pattern.compile("\\b(intern|internship|co-?op|trainee|apprentice)\\b");
    private static final Pattern NEW_GRAD_TITLE = Pattern.compile(
            "\\b(new grad|graduate|university|campus|early career|entry[- ]level|fresher|junior|associate software|"
                    + "engineer i|sde[- ]?(i|1)|software engineer (i|1)|swe (i|1))\\b");

    /** A title that says "intern" or "co-op" is an internship whichever list it came from. */
    static JobKind of(String role, JobKind fallback) {
        String r = role.toLowerCase(Locale.ROOT);
        if (INTERN_TITLE.matcher(r).find()) {
            return INTERNSHIP;
        }
        if (fallback == FULL_TIME && NEW_GRAD_TITLE.matcher(r).find()) {
            return NEW_GRAD;
        }
        return fallback;
    }
}
