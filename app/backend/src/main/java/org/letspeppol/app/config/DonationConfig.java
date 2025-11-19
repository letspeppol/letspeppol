package org.letspeppol.app.config;

import io.netty.channel.ChannelOption;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class DonationConfig {

    @Bean(name = "OpenCollectiveWebClient")
    public WebClient webClient(@Value("${open-collective.url}") String apiUrl, @Value("${open-collective.api-key}") String apiKey) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofSeconds(3));

        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Api-Key", apiKey)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
