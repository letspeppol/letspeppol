package org.letspeppol.app.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class ProxyConfig {

    @Bean
    public ReactiveClientRegistrationRepository appClientRegistrationRepository(
            @Value("${app.service-client.id:kyc-service}") String clientId,
            @Value("${app.service-client.secret}") String clientSecret,
            @Value("${app.service-client.token-uri}") String tokenUri) {

        ClientRegistration registration = ClientRegistration.withRegistrationId("app-service")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tokenUri(tokenUri)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("service")
                .build();

        return new InMemoryReactiveClientRegistrationRepository(registration);
    }

    private HttpClient createHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(30))
                        .addHandlerLast(new WriteTimeoutHandler(30))
                );
    }

    /// WebClient for user-context proxy calls (token forwarded from request)
    @Bean
    public WebClient proxyWebClient(@Value("${proxy.api.url}") String apiUrl) {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .build();
    }

    /// WebClient for service-to-service proxy calls (auto Client Credentials)
    @Bean
    public WebClient serviceProxyWebClient(
            @Value("${proxy.api.url}") String apiUrl,
            ReactiveClientRegistrationRepository clientRegistrationRepository) {

        var authorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
        var authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);

        var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId("app-service");

        return WebClient.builder()
                .baseUrl(apiUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .filter(oauth2Filter)
                .build();
    }
}
