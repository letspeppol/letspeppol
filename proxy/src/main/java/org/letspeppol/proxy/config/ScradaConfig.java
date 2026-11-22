package org.letspeppol.proxy.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.letspeppol.proxy.service.ScradaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@Conditional(ScradaConfig.HasApiKey.class)
@Import(ScradaService.class)
public class ScradaConfig {

    static class HasApiKey implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("scrada.api-key"));
        }
    }

    @Bean
    public WebClient scradaWebClient(
            @Value("${scrada.url}") String apiUrl,
            @Value("${scrada.company-id}") String companyId,
            @Value("${scrada.api-key}") String apiKey,
            @Value("${scrada.password}") String password) {
        Assert.hasText(companyId, "SCRADA_COMPANY_ID must be set when SCRADA_API_KEY is provided");
        Assert.hasText(password, "SCRADA_API_PWD must be set when SCRADA_API_KEY is provided");

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(30))
                        .addHandlerLast(new WriteTimeoutHandler(30))
                )
                .wiretap("reactor.netty.http.client",
                        io.netty.handler.logging.LogLevel.DEBUG,
                        reactor.netty.transport.logging.AdvancedByteBufFormat.TEXTUAL);

        return WebClient.builder()
                .baseUrl(apiUrl + "/v1/company/" + companyId + "/peppol")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .defaultHeader("X-API-KEY", apiKey)
                .defaultHeader("X-PASSWORD", password)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
