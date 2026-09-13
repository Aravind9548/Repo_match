package com.repomatch.jobs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What someone meant by a search box. "indian internship backend" becomes: kind = internship, country = India,
 * and one keyword concept "backend" (which also accepts "back-end" and "back end").
 *
 * <p>Words are grouped into concepts: a posting must match every concept, and any spelling within one.
 */
public record JobQuery(JobKind kind, String country, boolean remote, Set<String> cityAliases,
                       List<Set<String>> concepts) {

    private static final Set<String> STOP = Set.of("in", "at", "for", "jobs", "job", "role", "roles", "position",
            "positions", "opening", "openings", "hiring", "the", "a", "an", "and", "or", "of", "to", "with", "based",
            "near", "me", "opportunity", "opportunities", "vacancy", "vacancies", "summer", "2026", "2027", "location");

    private static final Set<String> INTERN_WORDS = Set.of("intern", "interns", "internship", "internships", "co-op",
            "coop", "co op", "trainee", "apprentice", "apprenticeship");
    private static final Set<String> NEW_GRAD_WORDS = Set.of("new grad", "newgrad", "graduate", "grad", "fresher",
            "freshers", "entry level", "entry-level", "university grad", "campus", "early career", "junior");
    private static final Set<String> FULL_TIME_WORDS = Set.of("full time", "full-time", "fulltime", "experienced");

    /** Alternate spellings people use for the same thing. Each key expands to all its values. */
    private static final Map<String, Set<String>> SYNONYMS = Map.ofEntries(
            Map.entry("sde", Set.of("sde", "software development engineer", "software engineer", "swe")),
            Map.entry("swe", Set.of("swe", "software engineer", "software development engineer", "sde")),
            Map.entry("software", Set.of("software")),
            Map.entry("backend", Set.of("backend", "back-end", "back end", "server")),
            Map.entry("frontend", Set.of("frontend", "front-end", "front end", "ui engineer", "web developer")),
            Map.entry("fullstack", Set.of("fullstack", "full-stack", "full stack")),
            Map.entry("full stack", Set.of("fullstack", "full-stack", "full stack")),
            Map.entry("ml", Set.of("ml", "machine learning", "ai/ml")),
            Map.entry("machine learning", Set.of("ml", "machine learning")),
            Map.entry("ai", Set.of("ai", "artificial intelligence", "machine learning", "ml", "llm", "genai")),
            Map.entry("data science", Set.of("data science", "data scientist")),
            Map.entry("devops", Set.of("devops", "sre", "site reliability", "platform engineer", "infrastructure")),
            Map.entry("sre", Set.of("sre", "site reliability", "devops")),
            Map.entry("android", Set.of("android", "mobile")),
            Map.entry("ios", Set.of("ios", "mobile", "swift")),
            Map.entry("mobile", Set.of("mobile", "android", "ios")),
            Map.entry("security", Set.of("security", "cyber", "infosec")),
            Map.entry("qa", Set.of("qa", "quality", "test engineer", "sdet")),
            Map.entry("pm", Set.of("pm", "product manager", "product management")),
            Map.entry("developer", Set.of("developer", "engineer", "programmer")),
            Map.entry("engineer", Set.of("engineer", "developer")));

    public static JobQuery parse(String q, String location) {
        JobKind kind = null;
        String country = null;
        boolean remote = false;
        Set<String> cities = new LinkedHashSet<>();
        List<Set<String>> concepts = new ArrayList<>();

        List<String> words = new ArrayList<>(List.of(Places.fold(q).replace(",", " ").split(" ")));
        words.removeIf(String::isBlank);

        for (int i = 0; i < words.size(); i++) {
            // Try two-word phrases first: "new grad", "machine learning", "new delhi".
            String two = i + 1 < words.size() ? words.get(i) + " " + words.get(i + 1) : null;
            String one = words.get(i);
            String matched = null;
            for (String candidate : two == null ? List.of(one) : List.of(two, one)) {
                if (INTERN_WORDS.contains(candidate)) {
                    kind = JobKind.INTERNSHIP;
                } else if (NEW_GRAD_WORDS.contains(candidate)) {
                    kind = JobKind.NEW_GRAD;
                } else if (FULL_TIME_WORDS.contains(candidate)) {
                    kind = JobKind.FULL_TIME;
                } else {
                    Places.Wanted place = Places.wanted(candidate);
                    if (place != null) {
                        if (place.country() != null) {
                            country = place.country();
                        }
                        remote |= place.remote();
                        cities.addAll(place.cityAliases());
                    } else if (SYNONYMS.containsKey(candidate)) {
                        concepts.add(concept(candidate, SYNONYMS.get(candidate)));
                    } else {
                        continue;
                    }
                }
                matched = candidate;
                break;
            }
            if (matched != null) {
                if (matched.equals(two)) {
                    i++;
                }
                continue;
            }
            if (!STOP.contains(one) && one.length() > 1) {
                concepts.add(concept(one, Set.of(one)));
            }
        }

        if (location != null && !location.isBlank()) {
            for (String part : location.split(",")) {
                Places.Wanted place = Places.wanted(part);
                if (place != null) {
                    if (place.country() != null) {
                        country = place.country();
                    }
                    remote |= place.remote();
                    cities.addAll(place.cityAliases());
                } else if (!Places.fold(part).isBlank()) {
                    // Unknown place: fall back to matching the words in the location text.
                    cities.add(Places.fold(part));
                }
            }
        }
        return new JobQuery(kind, country, remote, cities, concepts);
    }

    /** The word as typed comes first, so the summary echoes the person's own wording. */
    private static Set<String> concept(String typed, Set<String> alternatives) {
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(typed);
        ordered.addAll(alternatives.stream().sorted().toList());
        return java.util.Collections.unmodifiableSet(ordered);
    }

    /** A one-line summary of how the search was understood, shown above results. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (kind != null) {
            parts.add(switch (kind) {
                case INTERNSHIP -> "internships";
                case NEW_GRAD -> "new grad";
                case FULL_TIME -> "full-time";
            });
        }
        if (country != null) {
            parts.add("in " + capitalize(Places.countryName(country)));
        }
        if (!cityAliases.isEmpty()) {
            parts.add("near " + capitalize(cityAliases.iterator().next()));
        }
        if (remote) {
            parts.add("remote");
        }
        concepts.forEach(c -> parts.add("“" + c.iterator().next() + "”"));
        return String.join(" · ", parts);
    }

    private static String capitalize(String s) {
        return java.util.Arrays.stream(s.split(" "))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
