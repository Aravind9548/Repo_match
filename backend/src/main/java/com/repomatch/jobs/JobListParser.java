package com.repomatch.jobs;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls postings out of a job-list README.
 *
 * <p>These lists come in two shapes: HTML tables (SimplifyJobs, where a "↳" company cell means "same company as
 * the row above" and 🔒 marks a closed application) and markdown pipe tables (speedyapply and most others). Columns
 * are found by header name rather than position, so a list that adds a Salary column still parses.
 */
public final class JobListParser {

    public record Posting(String company, String role, String location, String applyUrl, boolean closed,
                          String ageLabel) {
    }

    private static final Pattern TABLE = Pattern.compile("<table\\b[^>]*>(.*?)</table>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL = Pattern.compile("<t([hd])[^>]*>(.*?)</t[hd]>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]*)]\\(([^)\\s]+)\\)");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final String CLOSED = "🔒";

    private JobListParser() {
    }

    public static List<Posting> parse(String markdown) {
        List<Posting> out = new ArrayList<>();
        Matcher tables = TABLE.matcher(markdown);
        while (tables.find()) {
            List<List<String>> rows = new ArrayList<>();
            Matcher r = ROW.matcher(tables.group(1));
            while (r.find()) {
                List<String> cells = new ArrayList<>();
                Matcher c = CELL.matcher(r.group(1));
                while (c.find()) {
                    cells.add(c.group(2));
                }
                rows.add(cells);
            }
            readTable(rows, out);
        }
        readMarkdownTables(markdown, out);
        return out;
    }

    private static void readMarkdownTables(String markdown, List<Posting> out) {
        List<List<String>> table = new ArrayList<>();
        for (String line : markdown.split("\n")) {
            String t = line.strip();
            if (t.startsWith("|") && t.endsWith("|") && t.length() > 1) {
                String inner = t.substring(1, t.length() - 1);
                if (inner.replace("|", "").replace("-", "").replace(":", "").isBlank()) {
                    continue; // the |---|---| separator
                }
                table.add(List.of(inner.split("\\|", -1)));
            } else if (!table.isEmpty()) {
                readTable(table, out);
                table = new ArrayList<>();
            }
        }
        if (!table.isEmpty()) {
            readTable(table, out);
        }
    }

    /** First row is the header; everything after is a posting. */
    private static void readTable(List<List<String>> rows, List<Posting> out) {
        if (rows.size() < 2) {
            return;
        }
        List<String> header = rows.getFirst().stream().map(h -> text(h).toLowerCase(Locale.ROOT)).toList();
        int company = column(header, "company");
        int role = column(header, "role", "position", "title");
        int location = column(header, "location");
        int link = column(header, "application", "apply", "link", "posting");
        int age = column(header, "age", "date", "posted");
        if (company < 0 || role < 0) {
            return; // not a job table (e.g. a table of contents)
        }

        String lastCompany = null;
        for (List<String> row : rows.subList(1, rows.size())) {
            if (row.size() <= Math.max(company, role)) {
                continue;
            }
            String companyText = text(row.get(company));
            if (companyText.equals("↳") && lastCompany != null) {
                companyText = lastCompany;
            }
            // Lists decorate names ("🔥 Lyft"); the badge isn't part of the company.
            companyText = companyText.replaceFirst("^[^\\p{L}\\p{N}]+", "").strip();
            String roleText = text(row.get(role));
            if (companyText.isBlank() || roleText.isBlank()) {
                continue;
            }
            lastCompany = companyText;

            String linkCell = link >= 0 && link < row.size() ? row.get(link) : "";
            boolean closed = row.stream().anyMatch(cell -> cell.contains(CLOSED));
            String url = applyUrl(linkCell);
            if (url == null && !closed) {
                continue;
            }
            if (url == null) {
                continue; // closed and no link left to track
            }
            out.add(new Posting(clip(companyText, 300), clip(roleText, 500),
                    location >= 0 && location < row.size() ? clip(text(row.get(location)), 500) : null,
                    normalizeUrl(url), closed,
                    age >= 0 && age < row.size() ? clip(text(row.get(age)), 20) : null));
        }
    }

    private static int column(List<String> header, String... names) {
        for (String name : names) {
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i).contains(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** The employer's link, not the aggregator's own page next to it. */
    private static String applyUrl(String cell) {
        List<String> urls = new ArrayList<>();
        Matcher h = HREF.matcher(cell);
        while (h.find()) {
            urls.add(h.group(1).replace("&amp;", "&"));
        }
        Matcher m = MD_LINK.matcher(cell);
        while (m.find()) {
            urls.add(m.group(2));
        }
        return urls.stream()
                .filter(u -> u.startsWith("http"))
                .filter(u -> !u.contains("simplify.jobs/p/") && !u.contains("i.imgur.com"))
                .findFirst()
                .orElse(null);
    }

    /** Strips utm_* / ref tracking so the same job from two lists dedups to one row. */
    static String normalizeUrl(String url) {
        try {
            URI uri = URI.create(url.strip());
            String query = uri.getRawQuery();
            String kept = null;
            if (query != null) {
                kept = String.join("&", java.util.Arrays.stream(query.split("&"))
                        .filter(p -> !p.toLowerCase(Locale.ROOT).startsWith("utm_") && !p.startsWith("ref=")
                                && !p.startsWith("source=") && !p.startsWith("gh_src="))
                        .toList());
                if (kept.isEmpty()) {
                    kept = null;
                }
            }
            String base = uri.getScheme() + "://" + uri.getRawAuthority() + (uri.getRawPath() == null ? "" : uri.getRawPath());
            return clip(kept == null ? base : base + "?" + kept, 2000);
        } catch (IllegalArgumentException e) {
            return clip(url.strip(), 2000);
        }
    }

    private static String text(String html) {
        String s = MD_LINK.matcher(html).replaceAll("$1");
        s = TAG.matcher(s).replaceAll(" ");
        s = s.replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
                .replace("**", "").replace(CLOSED, "");
        return s.replaceAll("\\s+", " ").strip();
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
