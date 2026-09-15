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
}
