package com.repomatch.jobs;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The searchable text stored with each posting, and the word-start matching used on it — the same rules in SQL
 * (for the feed) and in Java (for alerts), so a search and an alert never disagree.
 */
public final class SearchText {

    private SearchText() {
    }

    /** "Okta · Software Engineer Intern · Bengaluru, India" → "okta software engineer intern bengaluru india bangalore blr". */
    public static String build(String company, String role, String location, Places.Place place) {
        Set<String> parts = new LinkedHashSet<>();
        parts.add(Places.fold(company));
        parts.add(Places.fold(role));
        parts.add(Places.fold(location));
        parts.addAll(place.aliases());
        if (place.remote()) {
            parts.add("remote");
        }
        return String.join(" ", parts.stream().filter(s -> !s.isBlank()).toList());
    }

    /**
     * A regex matching any alternative as a whole word, allowing plural and -ing/-er endings: "engineer" matches
     * "engineers" and "engineering", but "java" doesn't match "javascript" and "india" doesn't match "indianapolis".
     * Written without {@code \b}, which means backspace in PostgreSQL.
     */
    public static String regex(Collection<String> alternatives) {
        String alts = alternatives.stream()
                .map(Places::fold)
                .filter(s -> !s.isBlank())
                .map(SearchText::escape)
                .collect(Collectors.joining("|"));
        return "(^|[^a-z0-9])(" + alts + ")(s|es|ing|er|ers)?($|[^a-z0-9])";
    }

    public static boolean matches(String searchText, Collection<String> alternatives) {
        if (alternatives.isEmpty()) {
            return true;
        }
        return Pattern.compile(regex(alternatives)).matcher(searchText == null ? "" : searchText).find();
    }

    /** Escapes for both Java and PostgreSQL regex engines. */
    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ("\\.[]{}()*+?^$|-#/".indexOf(c) >= 0) {
                b.append('\\');
            }
            b.append(c);
        }
        return b.toString();
    }
}
