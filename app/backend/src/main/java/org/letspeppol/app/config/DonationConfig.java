package org.letspeppol.app.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class DonationConfig {

    @Bean(name = "OpenCollectiveWebClient")
    public WebClient webClient(@Value("${open-collective.url}") String apiUrl, @Value("${open-collective.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Api-Key", apiKey)
                .build();
    }

    @PostConstruct
    public void init() {

    }

}
