package org.letspeppol.proxy.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class ScradaConfig {

    @Bean
    public WebClient scradaWebClient(@Value("${scrada.url}") String apiUrl, @Value("${scrada.company-id}") String companyId, @Value("${scrada.api-key}") String apiKey, @Value("${scrada.password}") String password) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .responseTimeout(Duration.ofSeconds(3));

        return WebClient.builder()
                .baseUrl(apiUrl + "/v1/company/" + companyId + "/peppol")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .defaultHeader("X-API-KEY", apiKey)
                .defaultHeader("X-PASSWORD", password)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
