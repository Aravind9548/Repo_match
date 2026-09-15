package com.repomatch.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.repomatch.config.AppProperties;

class JobWatchEntityTest {

    private static JobPostingEntity posting(String company, String role, String location, JobKind kind) {
        JobPostingEntity p = new JobPostingEntity();
        p.setCompany(company);
        p.setRole(role);
        p.setLocation(location);
        p.setKind(kind);
        Places.Place place = Places.of(location);
        p.setCountry(place.country());
        p.setRemote(place.remote());
        p.setSearchText(SearchText.build(company, role, location, place));
        return p;
    }

    @Test
    void anyKeywordMatchesExcludesVetoAndLocationsNarrow() {
        JobWatchEntity w = new JobWatchEntity();
        w.setKeywords(new String[] {"backend", "java"});
        w.setExcludeKeywords(new String[] {"senior"});
        w.setLocations(new String[] {"Remote", "Bangalore"});
        w.setKinds(Set.of(JobKind.INTERNSHIP));

        assertThat(w.matches(posting("Stripe", "Backend Engineer Intern", "Remote in US", JobKind.INTERNSHIP))).isTrue();
        assertThat(w.matches(posting("Stripe", "Senior Backend Intern", "Remote", JobKind.INTERNSHIP))).isFalse();
        assertThat(w.matches(posting("Stripe", "Backend Intern", "New York, NY", JobKind.INTERNSHIP))).isFalse();
        assertThat(w.matches(posting("Stripe", "Backend Engineer", "Remote", JobKind.NEW_GRAD))).isFalse();
        assertThat(w.matches(posting("Stripe", "Frontend Intern", "Remote", JobKind.INTERNSHIP))).isFalse();

        JobPostingEntity closed = posting("Stripe", "Backend Intern", "Remote", JobKind.INTERNSHIP);
        closed.setClosed(true);
        assertThat(w.matches(closed)).isFalse();
    }

    @Test
    void countriesCitySpellingsAndSynonymsWork() {
        JobWatchEntity w = new JobWatchEntity();
        w.setKeywords(new String[] {"sde"});
        w.setLocations(new String[] {"India"});

        assertThat(w.matches(posting("Okta", "Software Engineer Intern", "Bengaluru, India", JobKind.INTERNSHIP))).isTrue();
        assertThat(w.matches(posting("AES", "Software Engineer Intern", "Indianapolis, IN", JobKind.INTERNSHIP))).isFalse();

        JobWatchEntity city = new JobWatchEntity();
        city.setLocations(new String[] {"bangalore"});
        assertThat(city.matches(posting("Swiggy", "Backend Engineer", "Bengaluru, KA, India", JobKind.FULL_TIME))).isTrue();
    }

    @Test
    void emptyWatchMatchesEveryOpenPosting() {
        assertThat(new JobWatchEntity().matches(posting("Acme", "Anything", null, JobKind.NEW_GRAD))).isTrue();
    }

    @Test
    void titleDecidesKindOverTheListDefault() {
        assertThat(JobKind.of("Software Engineering Intern", JobKind.NEW_GRAD)).isEqualTo(JobKind.INTERNSHIP);
        assertThat(JobKind.of("Software Engineer I", JobKind.INTERNSHIP)).isEqualTo(JobKind.INTERNSHIP);
        assertThat(JobKind.of("Software Engineer I", JobKind.NEW_GRAD)).isEqualTo(JobKind.NEW_GRAD);
    }

    @Test
    void ageLabelsBecomeDurations() {
        assertThat(JobFeedService.ageOf("0d")).isEqualTo(Duration.ZERO);
        assertThat(JobFeedService.ageOf("12d")).isEqualTo(Duration.ofDays(12));
        assertThat(JobFeedService.ageOf("2mo")).isEqualTo(Duration.ofDays(60));
        assertThat(JobFeedService.ageOf("n/a")).isEqualTo(Duration.ZERO);
    }

    @Test
    void signedLinksResolveToTheOwnerAndTamperingFails() {
        OwnerKeys keys = new OwnerKeys(new AppProperties(null, null, null, null, null,
                new AppProperties.Notify(null, null, null, "a-test-secret-that-is-long-enough"), null));
        String raw = OwnerKeys.newKey();
        String owner = keys.resolve(raw);

        String link = keys.signedLink(owner);
        assertThat(keys.resolve(link)).isEqualTo(owner);

        String forged = "s." + OwnerKeys.sha256("someone-else-entirely-xx") + "." + link.substring(link.lastIndexOf('.') + 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> keys.resolve(forged))
                .isInstanceOf(OwnerKeys.MissingOwnerKeyException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> keys.resolve("short"))
                .isInstanceOf(OwnerKeys.MissingOwnerKeyException.class);
    }
}
