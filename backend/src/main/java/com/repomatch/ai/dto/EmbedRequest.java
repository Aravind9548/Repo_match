package com.repomatch.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** @param kind "document" for stored content, "query" for a question. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EmbedRequest(List<String> texts, String kind) {

    public static EmbedRequest documents(List<String> texts) {
        return new EmbedRequest(texts, "document");
    }

    public static EmbedRequest query(String text) {
        return new EmbedRequest(List.of(text), "query");
    }
}
