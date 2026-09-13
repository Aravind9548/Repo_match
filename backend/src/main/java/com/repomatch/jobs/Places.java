package com.repomatch.jobs;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns free-text locations ("Bengaluru-VTP, India", "Indianapolis, IN", "Remote in Canada") into a country code,
 * a remote flag and the alternate spellings a person might search for.
 *
 * <p>Deliberately a lookup table, not a model: it's instant, free and predictable. The trap it exists to avoid is
 * substring matching, where "india" hits "Indianapolis" and ", IN" (Indiana) looks like India.
 */
public final class Places {

    public record Place(String country, boolean remote, Set<String> aliases) {
    }

    /** Spellings that mean the same city. Every member expands to all of them. */
    private static final List<Set<String>> CITY_ALIASES = List.of(
            Set.of("bengaluru", "bangalore", "blr"),
            Set.of("gurugram", "gurgaon", "ggn"),
            Set.of("mumbai", "bombay"),
            Set.of("chennai", "madras"),
            Set.of("kolkata", "calcutta"),
            Set.of("new delhi", "delhi", "ncr", "delhi ncr"),
            Set.of("thiruvananthapuram", "trivandrum"),
            Set.of("pune", "poona"),
            Set.of("new york", "nyc", "new york city"),
            Set.of("san francisco", "sf", "bay area"),
            Set.of("washington dc", "washington, dc", "dc"));

    private static final Map<String, String> COUNTRY_WORDS = Map.ofEntries(
            Map.entry("india", "IN"), Map.entry("indian", "IN"), Map.entry("bharat", "IN"),
            Map.entry("usa", "US"), Map.entry("united states", "US"), Map.entry("us", "US"), Map.entry("america", "US"),
            Map.entry("american", "US"),
            Map.entry("canada", "CA"), Map.entry("canadian", "CA"),
            Map.entry("uk", "GB"), Map.entry("united kingdom", "GB"), Map.entry("england", "GB"), Map.entry("london", "GB"),
            Map.entry("germany", "DE"), Map.entry("berlin", "DE"), Map.entry("munich", "DE"),
            Map.entry("ireland", "IE"), Map.entry("dublin", "IE"),
            Map.entry("singapore", "SG"),
            Map.entry("netherlands", "NL"), Map.entry("amsterdam", "NL"),
            Map.entry("france", "FR"), Map.entry("paris", "FR"),
            Map.entry("japan", "JP"), Map.entry("tokyo", "JP"),
            Map.entry("australia", "AU"), Map.entry("sydney", "AU"),
            Map.entry("poland", "PL"), Map.entry("mexico", "MX"), Map.entry("brazil", "BR"),
            Map.entry("israel", "IL"), Map.entry("uae", "AE"), Map.entry("dubai", "AE"));

    /** Places that only exist in one country, so seeing the city settles it. */
    private static final Map<String, String> CITY_COUNTRY = Map.ofEntries(
            Map.entry("bengaluru", "IN"), Map.entry("bangalore", "IN"), Map.entry("hyderabad", "IN"),
            Map.entry("pune", "IN"), Map.entry("chennai", "IN"), Map.entry("mumbai", "IN"), Map.entry("gurugram", "IN"),
            Map.entry("gurgaon", "IN"), Map.entry("noida", "IN"), Map.entry("new delhi", "IN"), Map.entry("delhi", "IN"),
            Map.entry("kolkata", "IN"), Map.entry("ahmedabad", "IN"), Map.entry("jaipur", "IN"), Map.entry("kochi", "IN"),
            Map.entry("coimbatore", "IN"), Map.entry("indore", "IN"), Map.entry("chandigarh", "IN"),
            Map.entry("thiruvananthapuram", "IN"), Map.entry("trivandrum", "IN"), Map.entry("mysuru", "IN"),
            Map.entry("mysore", "IN"), Map.entry("vizag", "IN"), Map.entry("visakhapatnam", "IN"),
            Map.entry("bhubaneswar", "IN"), Map.entry("karnataka", "IN"), Map.entry("telangana", "IN"),
            Map.entry("maharashtra", "IN"), Map.entry("tamil nadu", "IN"), Map.entry("haryana", "IN"),
            Map.entry("toronto", "CA"), Map.entry("vancouver", "CA"), Map.entry("montreal", "CA"), Map.entry("waterloo", "CA"),
            Map.entry("ontario", "CA"), Map.entry("quebec", "CA"), Map.entry("british columbia", "CA"),
            Map.entry("london", "GB"), Map.entry("dublin", "IE"), Map.entry("berlin", "DE"), Map.entry("munich", "DE"),
            Map.entry("amsterdam", "NL"), Map.entry("paris", "FR"), Map.entry("singapore", "SG"), Map.entry("tokyo", "JP"),
            Map.entry("sydney", "AU"), Map.entry("melbourne", "AU"), Map.entry("warsaw", "PL"), Map.entry("tel aviv", "IL"));

    /** "City, ST" with a US state code — this is where ", IN" means Indiana. */
    private static final Pattern US_STATE = Pattern.compile(
            "(^|[,\\s(])(AL|AK|AZ|AR|CA|CO|CT|DE|DC|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY)($|[\\s,)+])");
    private static final Pattern US_WORDS = Pattern.compile(
            "\\b(united states|usa|u\\.s\\.|new york|san francisco|seattle|boston|austin|chicago|los angeles|atlanta|denver|remote in us|remote - us|us remote|california|texas|washington)\\b");
    private static final Pattern REMOTE = Pattern.compile("\\b(remote|work from home|wfh|anywhere|distributed)\\b");

    private Places() {
    }

    public static Place of(String location) {
        if (location == null || location.isBlank()) {
            return new Place(null, false, Set.of());
        }
        String raw = location.strip();
        String l = fold(raw);
        Set<String> aliases = new LinkedHashSet<>();
        String country = null;

        for (Map.Entry<String, String> e : CITY_COUNTRY.entrySet()) {
            if (hasWord(l, e.getKey())) {
                country = e.getValue();
                aliases.add(e.getKey());
            }
        }
        for (Map.Entry<String, String> e : COUNTRY_WORDS.entrySet()) {
            // "us"/"uk" are only trusted as whole words in a location, never inside "status" etc.
            if (hasWord(l, e.getKey()) && !(e.getKey().equals("us") && !raw.matches(".*\\bUS\\b.*"))) {
                country = e.getValue();
            }
        }
        if (country == null && (US_STATE.matcher(raw).find() || US_WORDS.matcher(l).find())) {
            country = "US";
        }
        for (Set<String> group : CITY_ALIASES) {
            if (group.stream().anyMatch(a -> hasWord(l, a))) {
                aliases.addAll(group);
            }
        }
        if (country != null) {
            aliases.add(countryName(country));
        }
        return new Place(country, REMOTE.matcher(l).find(), aliases);
    }

    /** What a person typed into "location" or as part of a query: a country, a city, or remote. */
    public record Wanted(String country, boolean remote, Set<String> cityAliases) {
        boolean isEmpty() {
            return country == null && !remote && cityAliases.isEmpty();
        }
    }

    /** Returns null when the words aren't a place we recognise. */
    public static Wanted wanted(String words) {
        String w = fold(words);
        if (w.isBlank()) {
            return null;
        }
        if (REMOTE.matcher(w).matches()) {
            return new Wanted(null, true, Set.of());
        }
        if (COUNTRY_WORDS.containsKey(w)) {
            return new Wanted(COUNTRY_WORDS.get(w), false, CITY_COUNTRY.containsKey(w) ? expand(w) : Set.of());
        }
        if (CITY_COUNTRY.containsKey(w) || CITY_ALIASES.stream().anyMatch(g -> g.contains(w))) {
            return new Wanted(null, false, expand(w));
        }
        return null;
    }

    static Set<String> expand(String city) {
        return CITY_ALIASES.stream().filter(g -> g.contains(city)).findFirst().map(Set::copyOf).orElse(Set.of(city));
    }

    public static String countryName(String code) {
        return switch (code) {
            case "IN" -> "india";
            case "US" -> "united states";
            case "CA" -> "canada";
            case "GB" -> "united kingdom";
            case "DE" -> "germany";
            case "IE" -> "ireland";
            case "SG" -> "singapore";
            case "NL" -> "netherlands";
            case "FR" -> "france";
            case "JP" -> "japan";
            case "AU" -> "australia";
            case "PL" -> "poland";
            case "MX" -> "mexico";
            case "BR" -> "brazil";
            case "IL" -> "israel";
            case "AE" -> "united arab emirates";
            default -> code.toLowerCase(Locale.ROOT);
        };
    }

    /** ISO codes from APIs ("in", "IND") → our two-letter form. */
    public static String isoCountry(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.strip().toUpperCase(Locale.ROOT);
        return switch (c) {
            case "IND" -> "IN";
            case "USA" -> "US";
            case "CAN" -> "CA";
            case "GBR", "UK" -> "GB";
            case "DEU" -> "DE";
            default -> c.length() == 2 ? c : null;
        };
    }

    static boolean hasWord(String haystack, String word) {
        int i = haystack.indexOf(word);
        while (i >= 0) {
            boolean startOk = i == 0 || !Character.isLetterOrDigit(haystack.charAt(i - 1));
            int end = i + word.length();
            boolean endOk = end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            i = haystack.indexOf(word, i + 1);
        }
        return false;
    }

    /** Lowercase, accents stripped, punctuation spaced — "Bengaluru-VTP" → "bengaluru vtp". */
    static String fold(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N},.+#]+", " ").replaceAll("\\s+", " ").strip();
    }
}
