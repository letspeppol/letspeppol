package io.tubs.kyc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Bean(name = "AppWebClient")
    public WebClient webClient(@Value("${app.api.url}") String apiUrl) {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .build();
    }
}
