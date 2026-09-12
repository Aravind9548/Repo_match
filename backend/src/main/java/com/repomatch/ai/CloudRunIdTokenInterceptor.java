package com.repomatch.ai;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Signs calls to the private AI service on Cloud Run with a Google ID token for this service's
 * identity, fetched from the metadata server. Only active when an audience is configured.
 */
class CloudRunIdTokenInterceptor implements ClientHttpRequestInterceptor {

    private static final String METADATA_URL =
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience=";
    /** Google ID tokens live one hour; refresh well before that. */
    private static final Duration REFRESH_AFTER = Duration.ofMinutes(45);

    private final String audience;
    private final HttpClient metadata = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private String token;
    private Instant fetchedAt = Instant.EPOCH;

    CloudRunIdTokenInterceptor(String audience) {
        this.audience = audience;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().setBearerAuth(currentToken());
        return execution.execute(request, body);
    }

    private synchronized String currentToken() throws IOException {
        if (token == null || Instant.now().isAfter(fetchedAt.plus(REFRESH_AFTER))) {
            var req = java.net.http.HttpRequest.newBuilder(
                            URI.create(METADATA_URL + URLEncoder.encode(audience, StandardCharsets.UTF_8)))
                    .header("Metadata-Flavor", "Google")
                    .timeout(Duration.ofSeconds(5))
                    .build();
            try {
                var res = metadata.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new IOException("Metadata server returned HTTP " + res.statusCode() + " for an ID token");
                }
                token = res.body().strip();
                fetchedAt = Instant.now();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching an ID token", e);
            }
        }
        return token;
    }
}
