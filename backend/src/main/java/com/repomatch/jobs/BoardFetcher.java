package com.repomatch.jobs;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            case GITHUB_LIST -> throw new IllegalArgumentException("GitHub lists are read through the GitHub client");
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
