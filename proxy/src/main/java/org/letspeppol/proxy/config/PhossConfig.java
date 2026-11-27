package org.letspeppol.proxy.config;

import io.netty.channel.ChannelOption;
import org.letspeppol.proxy.service.PhossService;
import org.letspeppol.proxy.service.PhossSubmissionStore;
import org.letspeppol.proxy.service.PhossSmpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "phoss.enabled", havingValue = "true")
@Import({PhossService.class, PhossSubmissionStore.class, PhossSmpClient.class})
public class PhossConfig {
    @Bean
    public WebClient phossApWebClient(@Value("${phoss.ap-url}") String url,
                                     @Value("${phoss.token-file}") String tokenFile) throws Exception {
        Assert.hasText(tokenFile, "PHOSS_TOKEN_FILE is required when PHOSS is enabled");
        String token = Files.readString(Path.of(tokenFile)).strip();
        Assert.isTrue(!token.isBlank() && !token.contains("\n") && !token.contains("\r"),
                "PHOSS token file must contain one nonempty token");
        return client(url, Duration.ofMinutes(3)).defaultHeader("X-Token", token).build();
    }

    @Bean
    public WebClient phossSmpWebClient(@Value("${phoss.smp-url}") String url) {
        // Deliberately independent: never send AP credentials to SMP.
        return client(url, Duration.ofSeconds(30)).build();
    }

    private WebClient.Builder client(String url, Duration timeout) {
        URI uri = URI.create(url);
        Assert.isTrue(uri.getHost() != null && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                        && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null,
                "PHOSS URLs must be absolute HTTP(S) URLs without credentials, query or fragment");
        HttpClient http = HttpClient.create().followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000).responseTimeout(timeout);
        return WebClient.builder().baseUrl(url.replaceAll("/+$", ""))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .clientConnector(new ReactorClientHttpConnector(http));
    }
}
