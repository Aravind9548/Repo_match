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
}
