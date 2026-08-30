package com.example.disclosurereview.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient llmWebClient(LlmProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(properties.getTimeout());
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
