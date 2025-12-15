package org.letspeppol.kyc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ProxyConfig {

    @Bean(name = "ProxyWebClient")
    public WebClient webClient(@Value("${proxy.api.url}") String apiUrl) {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
    }
}
