package com.repomatch.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientsConfig {

    @Bean
    RestClient gitHubRestClient(RestClient.Builder builder, AppProperties props) {
        RestClient.Builder b = builder.clone()
                .baseUrl(props.github().baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "repomatch");
        if (props.github().hasToken()) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.github().token().trim());
        }
        return b.build();
    }

    @Bean
    RestClient aiRestClient(RestClient.Builder builder, AppProperties props) {
        HttpClient jdk = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.ai().connectTimeout())
                .build();
        var factory = new JdkClientHttpRequestFactory(jdk);
        // LLM explanations can take a while with thinking models.
        factory.setReadTimeout(props.ai().readTimeout());
        return builder.clone()
                .baseUrl(props.ai().baseUrl())
                .requestFactory(factory)
                .build();
    }
}
