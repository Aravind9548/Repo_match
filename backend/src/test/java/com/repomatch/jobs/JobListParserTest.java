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
    void ignoresTablesThatAreNotJobLists() {
        String md = """
                | Section | Link |
                |---|---|
                | Internships | [here](#internships) |
                """;

        assertThat(JobListParser.parse(md)).isEmpty();
    }
}
