package com.repomatch.jobs;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a link a search model returned really is the posting it claims to be.
 *
 * <p>Opening fine proves nothing: a search-results page ("in.indeed.com/q-data-science-internship-india-jobs.html")
 * opens fine too, and a model will happily label it with a company that isn't on it. So a link must look like a
 * single posting, and the page must actually mention the company and most of the title.
 */
public final class PostingCheck {

    /** Search and listing pages on the usual job sites — never a single posting. */
    private static final List<Pattern> LISTING_URLS = List.of(
            Pattern.compile("indeed\\.[a-z.]+/(q-|jobs\\b|m/jobs|cmp/[^/]+/jobs)"),
            Pattern.compile("indeed\\.[a-z.]+/.*-jobs(-in-[^/]+)?\\.html"),
            Pattern.compile("linkedin\\.com/jobs/(search|collections)|linkedin\\.com/jobs/?$|linkedin\\.com/jobs/[a-z-]+-jobs"),
            Pattern.compile("naukri\\.com/[a-z0-9-]+-jobs(-in-[a-z0-9-]+)?(-\\d+)?/?$"),
            Pattern.compile("internshala\\.com/internships(/|$)"),
            Pattern.compile("glassdoor\\.[a-z.]+/(Job|Jobs)/.*SRCH"),
            Pattern.compile("foundit\\.in/srp|shine\\.com/job-search|timesjobs\\.com/candidate/job-search"),
            Pattern.compile("[?&](q|query|keywords|search|searchText|k)=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/(search|jobs-search|job-search|search-jobs)(/|\\?|$)", Pattern.CASE_INSENSITIVE));

    /** Words too common in company names to prove anything. */
    private static final Set<String> COMPANY_NOISE = Set.of("inc", "ltd", "llc", "pvt", "private", "limited", "technologies",
            "technology", "solutions", "services", "india", "labs", "the", "and", "company", "corp", "corporation", "group",
            "software", "systems", "global", "international", "consulting", "digital");

    /** Words too common in titles to prove anything. */
    private static final Set<String> TITLE_NOISE = Set.of("intern", "interns", "internship", "the", "and", "for", "with",
            "2025", "2026", "2027", "summer", "winter", "remote", "india", "hybrid", "onsite", "full", "time", "part");

    private PostingCheck() {
    }

    public static boolean isListingUrl(String url) {
        if (url == null) {
            return true;
        }
        String u = url.toLowerCase(Locale.ROOT);
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank() || path.equals("/")) {
                return true; // a site's home page, not a posting
            }
        } catch (IllegalArgumentException e) {
            return true;
        }
        return LISTING_URLS.stream().anyMatch(p -> p.matcher(u).find());
    }

    /**
     * The page must name the company (any distinctive word of it) and contain most of the title's distinctive words.
     * Pages are often script-rendered, but the company and title still appear in the title tag or embedded JSON.
     */
    public static boolean pageMatches(String html, String title, String company) {
        if (html == null || html.isBlank()) {
            return false;
        }
        String page = Places.fold(html);
        List<String> companyWords = words(company, COMPANY_NOISE);
        boolean companyOk = companyWords.isEmpty() || companyWords.stream().anyMatch(w -> Places.hasWord(page, w));
        List<String> titleWords = words(title, TITLE_NOISE);
        long present = titleWords.stream().filter(w -> Places.hasWord(page, w)).count();
        boolean titleOk = titleWords.isEmpty() || present * 10 >= titleWords.size() * 6L;
        return companyOk && titleOk;
    }

    private static List<String> words(String s, Set<String> noise) {
        if (s == null || s.equalsIgnoreCase("Unknown company")) {
            return List.of();
        }
        return Arrays.stream(Places.fold(s).replace(",", " ").replace(".", " ").split(" "))
                .filter(w -> w.length() >= 3 && !noise.contains(w))
                .distinct()
                .toList();
    }
}
