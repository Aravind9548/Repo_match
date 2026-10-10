package com.repomatch.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostingCheckTest {

    @Test
    void searchAndListingPagesAreNotPostings() {
        // The real case: an Indeed search page labelled "Data Science Intern · Microsoft".
        assertThat(PostingCheck.isListingUrl("https://in.indeed.com/q-data-science-internship-india-jobs.html")).isTrue();
        assertThat(PostingCheck.isListingUrl("https://in.indeed.com/jobs?q=data+science&l=India")).isTrue();
        assertThat(PostingCheck.isListingUrl("https://www.linkedin.com/jobs/search/?keywords=intern")).isTrue();
        assertThat(PostingCheck.isListingUrl("https://www.naukri.com/data-science-internship-jobs-in-bangalore")).isTrue();
        assertThat(PostingCheck.isListingUrl("https://internshala.com/internships/data-science-internship")).isTrue();
        assertThat(PostingCheck.isListingUrl("https://careers.microsoft.com/")).isTrue();

        assertThat(PostingCheck.isListingUrl("https://in.indeed.com/viewjob?jk=abc123")).isFalse();
        assertThat(PostingCheck.isListingUrl("https://www.linkedin.com/jobs/view/4012345678")).isFalse();
        assertThat(PostingCheck.isListingUrl("https://internshala.com/internship/detail/data-science-internship-at-optimspace1")).isFalse();
        assertThat(PostingCheck.isListingUrl("https://jobs.lever.co/fampay/09c9d0cd-f422-4138-a065-8d45e71d01a7")).isFalse();
    }

    @Test
    void pageMustNameTheCompanyAndMostOfTheTitle() {
        String indeedSearch = "<title>Data Science Internship India Jobs</title> Data Science Intern Optimspace Remote "
                + "Intern-Data Science SigTuple Technologies";

        assertThat(PostingCheck.pageMatches(indeedSearch, "Data Science Intern", "Microsoft")).isFalse();
        assertThat(PostingCheck.pageMatches(indeedSearch, "Data Science Intern", "Optimspace")).isTrue();
        assertThat(PostingCheck.pageMatches("<title>Backend Engineer | Swiggy Careers</title>", "Data Scientist", "Swiggy")).isFalse();
        assertThat(PostingCheck.pageMatches("{\"hiringOrganization\":{\"name\":\"Rubrik, Inc.\"},\"title\":\"Software Engineer Intern\"}",
                "Software Engineer - Winter Intern", "Rubrik")).isTrue();
    }

    @Test
    void readsAClosedGoogleFormAsGoneEvenThoughItAnswersFine() {
        String form = """
                <html><head><title>New Graduate Hiring Drive | 2025 &amp; 2026 Graduates</title></head><body>
                <div>New Graduate Hiring Drive | 2025 &amp; 2026 Graduates</div>
                <div>The form New Graduate Hiring Drive | 2025 &amp; 2026 Graduates is no longer accepting responses.</div>
                <div>Try contacting the owner of the form if you think that this is a mistake.</div>
                </body></html>""";

        assertThat(PostingCheck.looksClosed(form)).isTrue();
    }

    @Test
    void readsTheUsualWaysABoardSaysTheRoleIsOver() {
        assertThat(PostingCheck.looksClosed("<p>This job is no longer posted.</p>")).isTrue();
        assertThat(PostingCheck.looksClosed("<p>This position has been filled.</p>")).isTrue();
        assertThat(PostingCheck.looksClosed("<div>Applications are closed for this drive</div>")).isTrue();
        assertThat(PostingCheck.looksClosed("<div>This job posting has expired.</div>")).isTrue();
    }

    @Test
    void leavesALivePostingAlone() {
        String live = """
                <html><body><h1>Software Engineering Intern</h1>
                <p>We are accepting applications from 2026 and 2027 graduates. Apply by 30 November.</p>
                <p>You will build data pipelines and work with a mentor.</p></body></html>""";

        assertThat(PostingCheck.looksClosed(live)).isFalse();
    }

    @Test
    void doesNotReadAPagesOwnCodeAsAClosureNotice() {
        // The phrase sits in a script, where a reader never sees it: that page is still open.
        String page = """
                <html><body><h1>Backend Intern</h1>
                <script>var messages = {expired: "This job posting has expired"};</script>
                <p>Apply below.</p></body></html>""";

        assertThat(PostingCheck.looksClosed(page)).isFalse();
    }

    @Test
    void aCareersIndexIsNotAPosting() {
        assertThat(PostingCheck.isListingUrl("https://terralogic.com/careers")).isTrue();
        assertThat(PostingCheck.isListingUrl("https://terralogic.com/careers/")).isTrue();
        assertThat(PostingCheck.isListingUrl("https://acme.io/jobs")).isTrue();
        assertThat(PostingCheck.isListingUrl("https://acme.io/current-openings")).isTrue();
    }

    @Test
    void aRoleUnderThatIndexStillIs() {
        assertThat(PostingCheck.isListingUrl("https://terralogic.com/careers/software-engineering-intern-2026-bangalore"))
                .isFalse();
        assertThat(PostingCheck.isListingUrl("https://boards.greenhouse.io/acme/jobs/4012345")).isFalse();
    }
}
