package com.repomatch.tailor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobDescriptionServiceTest {

    @Test
    void htmlBecomesReadableText() {
        String greenhouse = "&lt;p&gt;We build &amp;amp; ship.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Java&lt;/li&gt;&lt;li&gt;Kafka&lt;/li&gt;&lt;/ul&gt;";
        String text = JobDescriptionService.htmlToText(JobDescriptionService.unescape(greenhouse));

        assertThat(text).contains("We build & ship.").contains("• Java").contains("• Kafka");
        assertThat(text).doesNotContain("<").doesNotContain("&lt;");
    }

    @Test
    void findsGreenhouseIdsOnCompanyDomainsToo() {
        assertThat(JobDescriptionService.greenhouseId("https://www.rubrik.com/company/careers/departments/job.8166537?gh_jid=8166537"))
                .isEqualTo("8166537");
        assertThat(JobDescriptionService.greenhouseId("https://job-boards.greenhouse.io/postman/jobs/7412345")).isEqualTo("7412345");
        assertThat(JobDescriptionService.greenhouseId("https://jobs.lever.co/x/1")).isNull();
    }
}
