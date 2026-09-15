package com.repomatch.tailor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gets the full description behind a posting, so tailoring works from what the employer actually wrote.
 *
 * <p>Job boards publish descriptions through the same public APIs we sync from, so those are exact. Anything else
 * falls back to the page's schema.org JobPosting data, then its visible text. Cached on the posting.
 */
@Service
public class JobDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionService.class);
    static final int MAX_CHARS = 20_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final Pattern GREENHOUSE = Pattern.compile("greenhouse\\.io/([^/]+)/jobs/(\\d+)");
    private static final Pattern GH_JID = Pattern.compile("[?&]gh_jid=(\\d+)");
    private static final Pattern LEVER = Pattern.compile("jobs\\.lever\\.co/([^/]+)/([0-9a-f-]{36})");
    private static final Pattern ASHBY = Pattern.compile("jobs\\.ashbyhq\\.com/([^/]+)/([0-9a-f-]{36})");
    private static final Pattern SMARTRECRUITERS = Pattern.compile("jobs\\.smartrecruiters\\.com/([^/]+)/(\\d+)");
    private static final Pattern WORKDAY = Pattern.compile("https://(([a-z0-9-]+)\\.wd\\d+\\.myworkdayjobs\\.com)/(?:[a-z]{2}-[A-Z]{2}/)?([^/]+)(/job/.+)$");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(TIMEOUT).build();

    public JobDescriptionService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** The cached description, fetching it the first time. Empty string when nothing readable was found. */
    public String forPosting(long postingId) {
        List<String[]> rows = jdbc.query("""
                select p.apply_url, p.description, s.type, s.slug
                from job_posting p join job_source s on s.id = p.source_id where p.id = ?""",
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)}, postingId);
        if (rows.isEmpty()) {
            return "";
        }
        String[] row = rows.getFirst();
        if (row[1] != null && !row[1].isBlank()) {
            return row[1];
        }
        String text = fetch(row[0], row[2], row[3]);
        // Only a real description is cached; an empty result is retried next time.
        if (!text.isBlank()) {
            jdbc.update("update job_posting set description = ?, description_fetched_at = ? where id = ?",
                    text, Timestamp.from(Instant.now()), postingId);
        }
        return text;
    }

    /** Job id inside a Greenhouse link, whether on greenhouse.io or a company domain ("...?gh_jid=8166537"). */
    static String greenhouseId(String url) {
        Matcher m = GH_JID.matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        m = GREENHOUSE.matcher(url);
        return m.find() ? m.group(2) : null;
    }

    String fetch(String url, String sourceType, String sourceSlug) {
        try {
            String text = null;
            // We know which board a posting came from, so boards on company domains still use the exact API.
            if ("GREENHOUSE".equals(sourceType) && sourceSlug != null && greenhouseId(url) != null) {
                text = greenhouse(sourceSlug, greenhouseId(url));
            }
            if (text == null || text.isBlank()) {
                text = viaApi(url);
            }
            if (text == null || text.isBlank()) {
                text = viaPage(url);
            }
            return clip(text == null ? "" : text.strip());
        } catch (Exception e) {
            log.info("no description for {}: {}", url, e.getMessage());
            return "";
        }
    }

    private String viaApi(String url) throws Exception {
        Matcher m;
        if ((m = GREENHOUSE.matcher(url)).find()) {
            return greenhouse(m.group(1), m.group(2));
        }
        if ((m = LEVER.matcher(url)).find()) {
            JsonNode j = json(get("https://api.lever.co/v0/postings/" + m.group(1) + "/" + m.group(2)));
            StringBuilder b = new StringBuilder(j.path("descriptionPlain").asText(""));
            for (JsonNode list : j.path("lists")) {
                b.append("\n\n").append(list.path("text").asText("")).append("\n").append(htmlToText(list.path("content").asText("")));
            }
            return b.append("\n\n").append(j.path("additionalPlain").asText("")).toString();
        }
        if ((m = ASHBY.matcher(url)).find()) {
            JsonNode board = json(get("https://api.ashbyhq.com/posting-api/job-board/" + m.group(1)));
            for (JsonNode job : board.path("jobs")) {
                if (m.group(2).equals(job.path("id").asText())) {
                    String plain = job.path("descriptionPlain").asText("");
                    return plain.isBlank() ? htmlToText(job.path("descriptionHtml").asText("")) : plain;
                }
            }
            return null;
        }
        if ((m = SMARTRECRUITERS.matcher(url)).find()) {
            JsonNode j = json(get("https://api.smartrecruiters.com/v1/companies/" + m.group(1) + "/postings/" + m.group(2)));
            StringBuilder b = new StringBuilder();
            j.path("jobAd").path("sections").forEach(s -> b.append(s.path("title").asText("")).append("\n")
                    .append(htmlToText(s.path("text").asText(""))).append("\n\n"));
            return b.toString();
        }
        if ((m = WORKDAY.matcher(url)).find()) {
            JsonNode j = json(get("https://" + m.group(1) + "/wday/cxs/" + m.group(2) + "/" + m.group(3) + m.group(4)));
            return htmlToText(j.path("jobPostingInfo").path("jobDescription").asText(""));
        }
        return null;
    }

    private String greenhouse(String board, String id) throws Exception {
        JsonNode j = json(get("https://boards-api.greenhouse.io/v1/boards/" + board + "/jobs/" + id));
        // Greenhouse double-encodes: the content field is HTML with its tags entity-escaped.
        return htmlToText(unescape(j.path("content").asText("")));
    }

    /** Company sites and portals: prefer the page's structured JobPosting data, else its visible text. */
    private String viaPage(String url) throws Exception {
        Matcher gh = GH_JID.matcher(url);
        String html = get(url);
        if (html == null) {
            return null;
        }
        Matcher ld = Pattern.compile("<script[^>]+application/ld\\+json[^>]*>(.*?)</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html);
        while (ld.find()) {
            try {
                JsonNode node = json(ld.group(1).strip());
                for (JsonNode n : node.isArray() ? node : List.of(node)) {
                    if (n.path("@type").asText("").toLowerCase(Locale.ROOT).contains("jobposting")) {
                        String d = htmlToText(unescape(n.path("description").asText("")));
                        if (d.length() > 200) {
                            return d;
                        }
                    }
                }
            } catch (Exception ignored) {
                // malformed JSON-LD is common; try the next block
            }
        }
        if (gh.find()) {
            // Company career sites that embed a Greenhouse board ("?gh_jid=123") — ask Greenhouse for the text.
            Matcher board = Pattern.compile("boards(?:-api)?\\.greenhouse\\.io/(?:embed/job_board/js\\?for=|v1/boards/)?([a-z0-9]+)").matcher(html);
            if (board.find()) {
                String text = greenhouse(board.group(1), gh.group(1));
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        String cleaned = html.replaceAll("(?is)<(script|style|nav|header|footer|svg|noscript|aside|form)[^>]*>.*?</\\1>", " ");
        // The posting itself usually sits in <main>/<article> or a "description" block; site chrome is around it.
        for (String pattern : List.of(
                "(?is)<[a-z]+[^>]+(?:class|id)=\"[^\"]*(?:job-?description|jobdescription|description__text|posting-description)[^\"]*\"[^>]*>(.*)",
                "(?is)<main[^>]*>(.*?)</main>",
                "(?is)<article[^>]*>(.*?)</article>")) {
            Matcher block = Pattern.compile(pattern).matcher(cleaned);
            if (block.find()) {
                String text = htmlToText(block.group(1));
                if (text.length() > 400) {
                    return text;
                }
            }
        }
        String visible = htmlToText(cleaned);
        return visible.length() > 400 ? visible : null;
    }

    private String get(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; RepoMatch)")
                .header("Accept", "text/html,application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
        return r.statusCode() < 400 ? r.body() : null;
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body == null ? "null" : body);
    }

    static String htmlToText(String html) {
        if (html == null) {
            return "";
        }
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|h[1-6]|tr|ul|ol)>", "\n")
                .replaceAll("(?i)<li[^>]*>", "• ")
                .replaceAll("<[^>]+>", " ");
        s = unescape(s).replaceAll("&#?[a-zA-Z0-9]+;", " ").replace("\uFFFD", " ");
        return s.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n").strip();
    }

    static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&nbsp;", " ").replace("&amp;", "&");
    }

    private static String clip(String s) {
        return s.length() <= MAX_CHARS ? s : s.substring(0, MAX_CHARS);
    }
}
