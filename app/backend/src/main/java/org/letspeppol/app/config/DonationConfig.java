package org.letspeppol.app.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@Conditional(DonationConfig.HasOpenCollectiveApiKey.class)
public class DonationConfig {

    static class HasOpenCollectiveApiKey implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("open-collective.api-key"));
        }
    }

    @Bean
    public HttpGraphQlClient openCollectiveGraphQlClient(
            @Value("${open-collective.url}") String apiUrl,
            @Value("${open-collective.api-key}") String apiKey) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(30))
                        .addHandlerLast(new WriteTimeoutHandler(30))
                );

        WebClient webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Api-Key", apiKey)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        return HttpGraphQlClient.builder(webClient).build();
    }
}
