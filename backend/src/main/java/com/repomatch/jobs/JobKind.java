package com.repomatch.jobs;

import java.util.Locale;

public enum JobKind {
    INTERNSHIP, NEW_GRAD;

    /** A title that says "intern" or "co-op" is an internship whichever list it came from. */
    static JobKind of(String role, JobKind fallback) {
        String r = role.toLowerCase(Locale.ROOT);
        return r.contains("intern") || r.contains("co-op") || r.contains("coop") ? INTERNSHIP : fallback;
    }
}
