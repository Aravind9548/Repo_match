package com.repomatch.repo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReadmeCleanerTest {

    @Test
    void stripsBadgesImagesHtmlAndLinkUrls() {
        String md = """
                # MyApp
                [![Build](https://ci/badge.svg)](https://ci) ![logo](logo.png)
                <p align="center">Fast <b>Spring Boot</b> API</p>
                <!-- hidden note -->
                See [the docs](https://example.com/docs) for details.



                [ref]: https://example.com
                """;

        String cleaned = ReadmeCleaner.clean(md, 10_000);

        assertThat(cleaned).isEqualTo("""
                # MyApp

                Fast Spring Boot API

                See the docs for details.""");
    }

    @Test
    void truncatesAndReturnsNullForEmptyContent() {
        assertThat(ReadmeCleaner.clean("abcdefgh", 3)).isEqualTo("abc");
        assertThat(ReadmeCleaner.clean("![only](an-image.png)", 100)).isNull();
        assertThat(ReadmeCleaner.clean(null, 100)).isNull();
    }
}
