package com.repomatch.jobs;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads companies' public job boards. Greenhouse, Lever, Ashby and SmartRecruiters all publish open roles as
 * JSON without a key — the same data job search engines index.
 */
@Component
public class BoardFetcher {

    private static final int SMARTRECRUITERS_PAGE = 100;
    private static final int SMARTRECRUITERS_MAX = 1000;

    private final RestClient http;
    private final ObjectMapper mapper;

    public BoardFetcher(RestClient.Builder builder, ObjectMapper mapper) {
        HttpClient jdk = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.http = builder.clone().requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "repomatch-job-feed")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
        this.mapper = mapper;
    }

    public List<FetchedPosting> fetch(JobSourceEntity source) {
        String slug = source.getSlug();
        String company = source.getCompanyName();
        return switch (source.getType()) {
            case GREENHOUSE -> greenhouse(get("https://boards-api.greenhouse.io/v1/boards/" + slug + "/jobs"), company);
            case LEVER -> lever(get("https://api.lever.co/v0/postings/" + slug + "?mode=json"), company);
            case ASHBY -> ashby(get("https://api.ashbyhq.com/posting-api/job-board/" + slug), company);
            case SMARTRECRUITERS -> smartRecruiters(slug, company);
            case WORKDAY -> workday(slug, company);
            case GITHUB_LIST, GOOGLE_SEARCH -> throw new IllegalArgumentException(source.getType() + " isn't a job board");
        };
    }

    private JsonNode get(String url) {
        try {
            String body = http.get().uri(url).retrieve().body(String.class);
            return mapper.readTree(body == null ? "null" : body);
        } catch (RestClientException | java.io.IOException e) {
            throw new BoardFetchException(url.replaceFirst("\\?.*", "") + ": " + e.getMessage());
        }
    }

    static List<FetchedPosting> greenhouse(JsonNode root, String company) {
        List<FetchedPosting> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            String location = text(j.path("location").path("name"));
            Places.Place place = Places.of(location);
            Instant posted = instant(text(j.path("first_published")));
            out.add(new FetchedPosting(nonBlank(text(j.path("company_name")), company), text(j.path("title")), location,
                    place.country(), place.remote(), text(j.path("absolute_url")), false, null,
                    posted != null ? posted : instant(text(j.path("updated_at"))), false));
        }
        return out;
    }

    static List<FetchedPosting> lever(JsonNode root, String company) {
        List<FetchedPosting> out = new ArrayList<>();
        for (JsonNode j : root) {
            JsonNode cat = j.path("categories");
            List<String> locations = new ArrayList<>();
            cat.path("allLocations").forEach(l -> locations.add(l.asText()));
            String location = locations.isEmpty() ? text(cat.path("location")) : String.join(" · ", locations);
            Places.Place place = Places.of(location);
            String country = Places.isoCountry(text(j.path("country")));
            boolean remote = place.remote() || "remote".equalsIgnoreCase(text(j.path("workplaceType")));
            String commitment = text(cat.path("commitment")).toLowerCase(Locale.ROOT);
            out.add(new FetchedPosting(company, text(j.path("text")), location, country != null ? country : place.country(),
                    remote, text(j.path("hostedUrl")), false, null,
                    j.path("createdAt").canConvertToLong() ? Instant.ofEpochMilli(j.path("createdAt").asLong()) : null,
                    commitment.contains("intern")));
        }
        return out;
    }

    static List<FetchedPosting> ashby(JsonNode root, String company) {
        List<FetchedPosting> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            if (j.has("isListed") && !j.path("isListed").asBoolean()) {
                continue;
            }
            List<String> locations = new ArrayList<>();
            locations.add(text(j.path("location")));
            j.path("secondaryLocations").forEach(l -> locations.add(text(l.path("location"))));
            String addressCountry = text(j.path("address").path("postalAddress").path("addressCountry"));
            if (!addressCountry.isBlank()) {
                locations.add(addressCountry);
            }
            String location = String.join(" · ", locations.stream().filter(s -> !s.isBlank()).distinct().toList());
            Places.Place place = Places.of(location);
            out.add(new FetchedPosting(company, text(j.path("title")), location, place.country(),
                    place.remote() || j.path("isRemote").asBoolean(false), text(j.path("jobUrl")), false, null,
                    instant(text(j.path("publishedAt"))), "intern".equalsIgnoreCase(text(j.path("employmentType")))));
        }
        return out;
    }

    private List<FetchedPosting> smartRecruiters(String slug, String company) {
        List<FetchedPosting> out = new ArrayList<>();
        for (int offset = 0; offset < SMARTRECRUITERS_MAX; offset += SMARTRECRUITERS_PAGE) {
            JsonNode page = get("https://api.smartrecruiters.com/v1/companies/" + slug + "/postings?limit="
                    + SMARTRECRUITERS_PAGE + "&offset=" + offset);
            List<FetchedPosting> batch = smartRecruiters(page, slug, company);
            out.addAll(batch);
            if (batch.size() < SMARTRECRUITERS_PAGE || out.size() >= page.path("totalFound").asInt()) {
                break;
            }
        }
        return out;
    }

    static final int WORKDAY_PAGE = 20;
    static final int WORKDAY_MAX = 400;
    static final String WORKDAY_COUNTRY = "India";

    /**
     * Workday's career sites all answer the same JSON search. A site lists thousands of roles worldwide, so we ask
     * for India only: through the site's own country filter when it has one (exact), else by searching "India" and
     * keeping what reads as India.
     */
    private List<FetchedPosting> workday(String slug, String company) {
        String[] parts = slug.split("\\|");
        if (parts.length != 3) {
            throw new BoardFetchException("Workday slug must be host|tenant|site, got " + slug);
        }
        String host = parts[0];
        String site = parts[2];
        String url = "https://" + host + "/wday/cxs/" + parts[1] + "/" + site + "/jobs";

        JsonNode first = postJson(url, workdayBody(null, WORKDAY_COUNTRY, 0));
        Map.Entry<String, String> facet = indiaFacet(first);
        boolean exact = facet != null;
        JsonNode page = exact ? postJson(url, workdayBody(facet, "", 0)) : first;

        List<FetchedPosting> out = new ArrayList<>();
        int total = page.path("total").asInt();
        for (int offset = 0; offset < Math.min(total, WORKDAY_MAX); offset += WORKDAY_PAGE) {
            if (offset > 0) {
                page = postJson(url, workdayBody(facet, exact ? "" : WORKDAY_COUNTRY, offset));
            }
            List<FetchedPosting> batch = workdayJobs(page, host, site, company, exact);
            out.addAll(batch);
            if (page.path("jobPostings").size() < WORKDAY_PAGE) {
                break;
            }
        }
        return out;
    }

    private Map<String, Object> workdayBody(Map.Entry<String, String> facet, String searchText, int offset) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("appliedFacets", facet == null ? Map.of() : Map.of(facet.getKey(), List.of(facet.getValue())));
        body.put("limit", WORKDAY_PAGE);
        body.put("offset", offset);
        body.put("searchText", searchText);
        return body;
    }

    /**
     * Finds the filter value meaning exactly "India". Tenants differ: Citi has a top-level "Country_and_Jurisdiction"
     * facet, NVIDIA nests "locationHierarchy1" inside "locationMainGroup". Site-level values ("India, Pune") are
     * skipped — one country value covers them all.
     */
    static Map.Entry<String, String> indiaFacet(JsonNode page) {
        for (JsonNode f : page.path("facets")) {
            Map.Entry<String, String> hit = indiaIn(f);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** One facet, or a group of facets (values that themselves have values). */
    private static Map.Entry<String, String> indiaIn(JsonNode facet) {
        String param = text(facet.path("facetParameter"));
        String p = param.toLowerCase(Locale.ROOT);
        for (JsonNode v : facet.path("values")) {
            if (v.has("values")) {
                Map.Entry<String, String> nested = indiaIn(v);
                if (nested != null) {
                    return nested;
                }
            } else if ((p.contains("country") || p.contains("hierarchy"))
                    && WORKDAY_COUNTRY.equalsIgnoreCase(text(v.path("descriptor")))) {
                return Map.entry(param, text(v.path("id")));
            }
        }
        return null;
    }

    static List<FetchedPosting> workdayJobs(JsonNode page, String host, String site, String company, boolean allIndia) {
        List<FetchedPosting> out = new ArrayList<>();
        for (JsonNode j : page.path("jobPostings")) {
            String path = text(j.path("externalPath"));
            if (path.isBlank()) {
                continue;
            }
            String location = text(j.path("locationsText"));
            // "3 Locations" says nothing; the job path usually starts with the primary location instead.
            if (location.matches("\\d+ Locations?")) {
                location = path.replaceFirst("^/job/([^/]+)/.*", "$1").replace('-', ' ');
            }
            Places.Place place = Places.of(location);
            String country = allIndia ? "IN" : place.country();
            if (!allIndia && !"IN".equals(country)) {
                continue; // text search for "India" also matches Indianapolis and "Indiana"
            }
            String age = text(j.path("postedOn"));
            out.add(new FetchedPosting(company, text(j.path("title")), location, country, place.remote(),
                    "https://" + host + "/en-US/" + site + path, false, clip(age.replace("Posted ", ""), 20),
                    Instant.now().minus(JobFeedService.ageOf(age.toLowerCase(Locale.ROOT).replace("30+", "30"))), false));
        }
        return out;
    }

    private JsonNode postJson(String url, Object body) {
        try {
            String res = http.post().uri(url).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(String.class);
            return mapper.readTree(res == null ? "null" : res);
        } catch (RestClientException | java.io.IOException e) {
            throw new BoardFetchException(url + ": " + e.getMessage());
        }
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    static List<FetchedPosting> smartRecruiters(JsonNode root, String slug, String company) {
        List<FetchedPosting> out = new ArrayList<>();
        for (JsonNode j : root.path("content")) {
            JsonNode loc = j.path("location");
            String location = nonBlank(text(loc.path("fullLocation")),
                    String.join(", ", List.of(text(loc.path("city")), text(loc.path("country"))).stream()
                            .filter(s -> !s.isBlank()).toList()));
            Places.Place place = Places.of(location);
            String country = Places.isoCountry(text(loc.path("country")));
            String type = (text(j.path("typeOfEmployment").path("label")) + " "
                    + text(j.path("experienceLevel").path("label"))).toLowerCase(Locale.ROOT);
            out.add(new FetchedPosting(company, text(j.path("name")), location, country != null ? country : place.country(),
                    place.remote() || loc.path("remote").asBoolean(false),
                    "https://jobs.smartrecruiters.com/" + slug + "/" + text(j.path("id")), false, null,
                    instant(text(j.path("releasedDate"))), type.contains("intern")));
        }
        return out;
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? "" : n.asText("").strip();
    }

    private static String nonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static Instant instant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return Instant.parse(s);
            } catch (java.time.format.DateTimeParseException e2) {
                return null;
            }
        }
    }

    public static class BoardFetchException extends RuntimeException {
        public BoardFetchException(String message) {
            super(message);
        }
    }
}
