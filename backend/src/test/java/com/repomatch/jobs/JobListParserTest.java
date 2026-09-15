package com.repomatch.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class JobListParserTest {

    @Test
    void readsSimplifyHtmlTablesWithContinuationRowsAndClosedMarkers() {
        String md = """
                <table style="width: 100%">
                <thead><tr><th style="width: 25%">Company</th><th>Role</th><th>Location</th><th>Application</th><th>Age</th></tr></thead>
                <tbody>
                <tr>
                <td><strong><a href="https://simplify.jobs/c/Klaviyo?utm_source=GHList">Klaviyo</a></strong></td>
                <td>Software Engineer 1</td>
                <td>Boston, MA</td>
                <td><div align="center"><a href="https://job-boards.greenhouse.io/klaviyo/jobs/79?utm_source=Simplify&ref=Simplify"><img src="https://i.imgur.com/x.png" alt="Apply"></a> <a href="https://simplify.jobs/p/5077?utm_source=GHList"><img alt="Simplify"></a></div></td>
                <td>0d</td>
                </tr>
                <tr>
                <td>↳</td>
                <td>Backend Intern</td>
                <td>Remote</td>
                <td><a href="https://jobs.lever.co/klaviyo/abc">Apply</a></td>
                <td>2d</td>
                </tr>
                <tr>
                <td>Acme &amp; Co</td>
                <td>SWE</td>
                <td>NYC</td>
                <td>🔒</td>
                <td>9d</td>
                </tr>
                </tbody>
                </table>
                """;

        List<JobListParser.Posting> postings = JobListParser.parse(md);

        assertThat(postings).hasSize(2);
        assertThat(postings.get(0)).isEqualTo(new JobListParser.Posting("Klaviyo", "Software Engineer 1", "Boston, MA",
                "https://job-boards.greenhouse.io/klaviyo/jobs/79", false, "0d"));
        assertThat(postings.get(1).company()).isEqualTo("Klaviyo");
        assertThat(postings.get(1).applyUrl()).isEqualTo("https://jobs.lever.co/klaviyo/abc");
    }

    @Test
    void readsMarkdownPipeTablesByHeaderNameNotPosition() {
        String md = """
                # Jobs

                | Company | Position | Location | Salary | Posting | Age |
                |---|---|---|---|---|---|
                | 🔥 <a href="https://www.lyft.com"><strong>Lyft</strong></a> | Software Engineer Intern - Backend | San Francisco, CA | $58/hr | <a href="https://app.careerpuck.com/job-board/lyft/job/87?gh_jid=87"><img alt="Apply"/></a> | 0d |
                | [Stripe](https://stripe.com) | New Grad SWE | Remote | - | [Apply](https://stripe.com/jobs/1?utm_medium=x) | 3d |

                Some text after the table.
                """;

        List<JobListParser.Posting> postings = JobListParser.parse(md);

        assertThat(postings).extracting(JobListParser.Posting::company).containsExactly("Lyft", "Stripe");
        assertThat(postings.get(0).applyUrl()).isEqualTo("https://app.careerpuck.com/job-board/lyft/job/87?gh_jid=87");
        assertThat(postings.get(1).applyUrl()).isEqualTo("https://stripe.com/jobs/1");
        assertThat(postings.get(1).location()).isEqualTo("Remote");
    }

    @Test
    void readsIndiaListFormats() {
        // akshat0011: apply link in an unnamed last column, role cell links to an aggregator page.
        String unnamed = """
                | Company | Role | Location | Posted | |
                | --- | --- | --- | --- | --- |
                | [Kaleris](https://interndoor.com/companies/kaleris) | [Associate Software Engineer - Intern](https://interndoor.com/jobs/x) | Chennai | 12 Sept 2026 | [Apply](https://kaleris.wd501.myworkdayjobs.com/en-US/kaleris_careers/job/Chennai/R-100644) |
                """;
        JobListParser.Posting a = JobListParser.parse(unnamed).getFirst();
        assertThat(a.applyUrl()).isEqualTo("https://kaleris.wd501.myworkdayjobs.com/en-US/kaleris_careers/job/Chennai/R-100644");
        assertThat(a.location()).isEqualTo("Chennai");
        assertThat(JobListParser.dateOf(a.ageLabel())).hasToString("2026-09-12T00:00:00Z");

        // FresherFlow: skill tags under the title, short dates.
        String html = """
                <table><thead><tr><th>Company</th><th>Role</th><th>Location</th><th>Apply</th><th>Posted</th></tr></thead>
                <tbody><tr>
                <td><strong>ixigo</strong></td>
                <td>Full-Stack Intern<br><small>Python, Node.js, Postgres</small></td>
                <td>Gurugram</td>
                <td align="center"><a href="https://jobs.smartrecruiters.com/ixigo/744000148960832"><img alt="Apply" src="https://img.shields.io/badge/Apply"></a></td>
                <td>14 Sep 26</td>
                </tr></tbody></table>
                """;
        JobListParser.Posting f = JobListParser.parse(html).getFirst();
        assertThat(f.role()).isEqualTo("Full-Stack Intern");
        assertThat(JobListParser.dateOf(f.ageLabel())).hasToString("2026-09-14T00:00:00Z");
        assertThat(JobListParser.dateOf("Sep 11, 2026")).hasToString("2026-09-11T00:00:00Z");
        assertThat(JobListParser.dateOf("3d")).isNull();
    }

    @Test
    void ignoresTablesThatAreNotJobLists() {
        String md = """
                | Section | Link |
                |---|---|
                | Internships | [here](#internships) |
                """;

        assertThat(JobListParser.parse(md)).isEmpty();
    }
}
