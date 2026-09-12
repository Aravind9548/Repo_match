package com.repomatch.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomatch.ai.dto.AnswerRequest;
import com.repomatch.ai.dto.AnswerResponse;
import com.repomatch.ai.dto.EmbedRequest;
import com.repomatch.ai.dto.EmbedResponse;
import com.repomatch.ai.dto.ExplainRequest;
import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.ai.dto.RankRequest;
import com.repomatch.ai.dto.RankResponse;

/** HTTP client for the Python AI service (embeddings, ranking, LLM explanations). */
@Component
public class AiServiceClient {

    private final RestClient http;
    private final ObjectMapper mapper;

    public AiServiceClient(@Qualifier("aiRestClient") RestClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    public RankResponse rank(RankRequest request) {
        return post("/rank", request, RankResponse.class);
    }

    public ExplainResponse explain(ExplainRequest request) {
        return post("/explain", request, ExplainResponse.class);
    }

    /** General embeddings — used to index repository code for "ask your codebase". */
    public EmbedResponse embed(EmbedRequest request) {
        return post("/embed", request, EmbedResponse.class);
    }

    /** Answers a question from retrieved code chunks, with citations verified against them. */
    public AnswerResponse answer(AnswerRequest request) {
        return post("/answer", request, AnswerResponse.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return http.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException e) {
            throw new AiServiceException("AI service " + path + " failed: " + detailOf(e));
        } catch (RestClientException e) {
            throw new AiServiceException("AI service unreachable: " + e.getMessage());
        }
    }

    /** FastAPI puts the error message under "detail". */
    private String detailOf(RestClientResponseException e) {
        try {
            JsonNode node = mapper.readTree(e.getResponseBodyAsString());
            if (node.hasNonNull("detail")) {
                return node.get("detail").isTextual() ? node.get("detail").asText() : node.get("detail").toString();
            }
        } catch (Exception ignored) {
            // fall through to the status line
        }
        return "HTTP " + e.getStatusCode().value();
    }
}
