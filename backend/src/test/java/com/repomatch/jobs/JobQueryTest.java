package com.repomatch.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class JobQueryTest {

    @Test
    void indianInternshipMeansCountryAndKindNotKeywords() {
        JobQuery q = JobQuery.parse("indian internship", null);

        assertThat(q.kind()).isEqualTo(JobKind.INTERNSHIP);
        assertThat(q.country()).isEqualTo("IN");
        assertThat(q.concepts()).isEmpty();
        assertThat(q.describe()).isEqualTo("internships · in India");
    }

    @Test
    void phrasesSynonymsAndCitiesAreRecognised() {
        JobQuery q = JobQuery.parse("SDE intern backend jobs in Bangalore", null);

        assertThat(q.kind()).isEqualTo(JobKind.INTERNSHIP);
        assertThat(q.cityAliases()).contains("bengaluru", "bangalore");
        assertThat(q.concepts()).hasSize(2);
        assertThat(q.concepts().get(0)).contains("software engineer");
        assertThat(q.concepts().get(1)).contains("back-end");

        JobQuery ng = JobQuery.parse("new grad machine learning remote", "");
        assertThat(ng.kind()).isEqualTo(JobKind.NEW_GRAD);
        assertThat(ng.remote()).isTrue();
        assertThat(ng.concepts()).singleElement().satisfies(c -> assertThat(c).containsExactlyInAnyOrder("ml", "machine learning"));
        assertThat(JobQuery.parse("backend intern", null).describe()).isEqualTo("internships · “backend”");
    }

    @Test
    void summaryEchoesTheCityAsTyped() {
        assertThat(JobQuery.parse("intern pune", null).describe()).isEqualTo("internships · near Pune");
        assertThat(JobQuery.parse("intern bangalore", null).describe()).isEqualTo("internships · near Bangalore");
    }

    @Test
    void seasonsAreSearchable() {
        JobQuery q = JobQuery.parse("summer internship india", null);
        assertThat(q.kind()).isEqualTo(JobKind.INTERNSHIP);
        assertThat(q.country()).isEqualTo("IN");
        assertThat(q.describe()).isEqualTo("internships · in India · “summer”");
        assertThat(SearchText.matches("sde intern summer 2027 bengaluru", q.concepts().getFirst())).isTrue();
        assertThat(JobQuery.parse("6 month intern", null).concepts().getFirst()).contains("6-month");
    }

    @Test
    void locationFieldAcceptsCountriesCitiesAndUnknownPlaces() {
        assertThat(JobQuery.parse("", "India").country()).isEqualTo("IN");
        assertThat(JobQuery.parse("", "gurgaon").cityAliases()).contains("gurugram");
        assertThat(JobQuery.parse("", "Springfield").cityAliases()).containsExactly("springfield");
    }

    @Test
    void placesTellIndiaFromIndiana() {
        assertThat(Places.of("Indianapolis, IN").country()).isEqualTo("US");
        assertThat(Places.of("Bengaluru-VTP, India").country()).isEqualTo("IN");
        assertThat(Places.of("Bengaluru-VTP, India").aliases()).contains("bangalore", "india");
        assertThat(Places.of("Hyderabad").country()).isEqualTo("IN");
        assertThat(Places.of("Remote in Canada").country()).isEqualTo("CA");
        assertThat(Places.of("Remote in Canada").remote()).isTrue();
        assertThat(Places.of("San Francisco, CA").country()).isEqualTo("US");
        assertThat(Places.of("Status page team").country()).isNull();
    }

    @Test
    void wordMatchingAllowsEndingsButNotLongerWords() {
        String text = SearchText.build("Okta", "Software Engineering Intern", "Bengaluru, India", Places.of("Bengaluru, India"));

        assertThat(SearchText.matches(text, Set.of("engineer"))).isTrue();
        assertThat(SearchText.matches(text, Set.of("bangalore"))).isTrue();
        assertThat(SearchText.matches(text, Set.of("india"))).isTrue();
        assertThat(SearchText.matches("indianapolis in", Set.of("india"))).isFalse();
        assertThat(SearchText.matches("javascript developer", Set.of("java"))).isFalse();
        assertThat(SearchText.matches("c++ developer", Set.of("c++"))).isTrue();
    }
}
