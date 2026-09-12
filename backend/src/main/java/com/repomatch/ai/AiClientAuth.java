package com.repomatch.ai;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/** Factory for how the backend authenticates to the AI service. */
public final class AiClientAuth {

    private AiClientAuth() {
    }

    /** Google ID token for a private Cloud Run service; the audience is that service's URL. */
    public static ClientHttpRequestInterceptor idTokenFor(String audience) {
        return new CloudRunIdTokenInterceptor(audience);
    }
}
