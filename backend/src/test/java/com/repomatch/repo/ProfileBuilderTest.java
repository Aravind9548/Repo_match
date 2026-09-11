package com.repomatch.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ProfileBuilderTest {

    @Test
    void buildsProfileWithLanguagePercentagesSortedBySize() {
        RepoEntity repo = new RepoEntity();
        repo.setName("docuflow");
        repo.setDescription("Document workflow service");
        repo.setTopics(List.of("spring-boot", "postgres"));
        Map<String, Long> langs = new LinkedHashMap<>();
        langs.put("Dockerfile", 50L);
        langs.put("Java", 7000L);
        langs.put("HTML", 2950L);
        repo.setLanguages(langs);
        repo.setReadme("Upload documents via REST.");

        assertThat(ProfileBuilder.build(repo)).isEqualTo("""
                Project: docuflow
                Description: Document workflow service
                Topics: spring-boot, postgres
                Languages: Java 70%, HTML 30%
                README:
                Upload documents via REST.""");
    }

    @Test
    void omitsMissingSections() {
        RepoEntity repo = new RepoEntity();
        repo.setName("bare");
        assertThat(ProfileBuilder.build(repo)).isEqualTo("Project: bare");
    }

    @Test
    void hashIsStable() {
        assertThat(ProfileBuilder.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
