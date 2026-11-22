package org.letspeppol.proxy.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.letspeppol.proxy.service.RecommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@Conditional(RecommandConfig.HasAnyCredential.class)
@Import(RecommandService.class)
public class RecommandConfig {

    static class HasAnyCredential implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var environment = context.getEnvironment();
            return StringUtils.hasText(environment.getProperty("recommand.api-key"))
                    || StringUtils.hasText(environment.getProperty("recommand.api-secret"));
        }
    }

    @Bean
    public WebClient recommandWebClient(
            @Value("${recommand.url}") String apiUrl,
            @Value("${recommand.api-key}") String apiKey,
            @Value("${recommand.api-secret}") String apiSecret
    ) {
        Assert.isTrue(StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret),
                "Set both RECOMMAND_API_KEY and RECOMMAND_API_SECRET, or leave both blank");

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(30))
                        .addHandlerLast(new WriteTimeoutHandler(30))
                );

        return WebClient.builder()
                .baseUrl(apiUrl + "/api/v1")
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(apiKey, apiSecret);
                    headers.set(HttpHeaders.ACCEPT, "application/json");
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
