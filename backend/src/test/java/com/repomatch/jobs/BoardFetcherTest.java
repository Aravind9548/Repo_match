package com.repomatch.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Shapes copied from the live APIs (trimmed), so a format change shows up here first. */
class BoardFetcherTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void greenhouse() throws Exception {
        List<FetchedPosting> p = BoardFetcher.greenhouse(json.readTree("""
                {"jobs":[{"absolute_url":"https://job-boards.eu.greenhouse.io/groww/jobs/4880153101",
                  "location":{"name":"Bengaluru-VTP, India"},"id":4880153101,"updated_at":"2026-08-27T07:35:34-04:00",
                  "title":"SDE Intern","company_name":"Groww","first_published":"2026-06-03T02:46:08-04:00"}]}"""), "Groww");

        assertThat(p).singleElement().satisfies(x -> {
            assertThat(x.role()).isEqualTo("SDE Intern");
            assertThat(x.country()).isEqualTo("IN");
            assertThat(x.postedAt()).hasToString("2026-06-03T06:46:08Z");
        });
        assertThat(JobFeedService.canonicalUrl(p.getFirst().applyUrl()))
                .isEqualTo("https://job-boards.greenhouse.io/groww/jobs/4880153101");
    }

    @Test
    void leverUsesCountryFieldAndCommitment() throws Exception {
        List<FetchedPosting> p = BoardFetcher.lever(json.readTree("""
                [{"text":"Backend Intern","hostedUrl":"https://jobs.lever.co/fampay/09c9d0cd-f422-4138-a065-8d45e71d01a7",
                  "categories":{"commitment":"Internship","location":"Bengaluru","allLocations":["Bengaluru"]},
                  "createdAt":1785842845029,"country":"IN","workplaceType":"onsite"}]"""), "FamPay");

        assertThat(p.getFirst().country()).isEqualTo("IN");
        assertThat(p.getFirst().intern()).isTrue();
        assertThat(p.getFirst().company()).isEqualTo("FamPay");
    }

    @Test
    void ashbySkipsUnlistedAndReadsRemote() throws Exception {
        List<FetchedPosting> p = BoardFetcher.ashby(json.readTree("""
                {"jobs":[
                  {"title":"ML Intern","location":"Bengaluru","secondaryLocations":[],"publishedAt":"2026-06-17T02:47:19.937+00:00",
                   "isListed":true,"isRemote":true,"employmentType":"Intern","jobUrl":"https://jobs.ashbyhq.com/sarvam/a2fd44d1-1457-4248-8d9c-905953016398"},
                  {"title":"Hidden","location":"sf","isListed":false,"jobUrl":"https://jobs.ashbyhq.com/x/y"}]}"""), "Sarvam AI");

        assertThat(p).singleElement().satisfies(x -> {
            assertThat(x.remote()).isTrue();
            assertThat(x.intern()).isTrue();
            assertThat(x.country()).isEqualTo("IN");
        });
    }

    @Test
    void smartRecruitersBuildsJobUrl() throws Exception {
        List<FetchedPosting> p = BoardFetcher.smartRecruiters(json.readTree("""
                {"totalFound":1,"content":[{"id":"6000000001400145","name":"Product Manager I","releasedDate":"2026-09-13T10:50:19.975Z",
                  "location":{"city":"Bengaluru","country":"in","remote":false,"fullLocation":"Bengaluru, KA, India"},
                  "typeOfEmployment":{"label":"Full-time"}}]}"""), "Swiggy", "Swiggy");

        assertThat(p.getFirst().applyUrl()).isEqualTo("https://jobs.smartrecruiters.com/Swiggy/6000000001400145");
        assertThat(p.getFirst().country()).isEqualTo("IN");
        assertThat(p.getFirst().intern()).isFalse();
    }
}
