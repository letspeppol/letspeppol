package org.letspeppol.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class PeppolDirectoryConfig {

    @Bean(name = "PeppolDirectoryWebClient")
    public WebClient webClient(@Value("${peppol.directory.url}") String apiUrl) {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .build();
    }
}