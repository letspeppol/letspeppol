package org.letspeppol.app.config;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.config.TokenRevocationFeed.RevokedToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenRevocationFeedTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final URI FEED_URI = URI.create("http://kyc:8084/lapi/revocations");

    private final MutableClock clock = new MutableClock();
    private final List<URI> requestedUris = new ArrayList<>();
    private ExchangeFunction kyc = request -> Mono.error(new IllegalStateException("not stubbed"));
    private final TokenRevocationFeed feed = new TokenRevocationFeed(
            WebClient.builder().exchangeFunction(request -> {
                requestedUris.add(request.url());
                return kyc.exchange(request);
            }).build(),
            FEED_URI,
            clock);

    @Test
    void feedUriFollowsTheJwksUriUnlessConfigured() {
        assertThat(TokenRevocationFeed.feedUri("", "http://kyc:8084/auth/oauth2/jwks"))
                .isEqualTo(URI.create("http://kyc:8084/lapi/revocations"));
        assertThat(TokenRevocationFeed.feedUri(null, "https://example.org/kyc/auth/oauth2/jwks"))
                .isEqualTo(URI.create("https://example.org/kyc/lapi/revocations"));
        assertThat(TokenRevocationFeed.feedUri(" ", "http://kyc:8084/custom/keys"))
                .isEqualTo(URI.create("http://kyc:8084/lapi/revocations"));
        assertThat(TokenRevocationFeed.feedUri("http://other:1/feed", "http://kyc:8084/auth/oauth2/jwks"))
                .isEqualTo(URI.create("http://other:1/feed"));
    }

    @Test
    void tokenListedByKycIsRejectedAfterTheNextRefresh() {
        kyc = respondWith("""
                [{"jti":"revoked-token","expiresAt":"2026-10-06T10:31:00Z"}]
                """);
        assertThat(feed.validate(jwt("revoked-token")).hasErrors()).isFalse();

        feed.refresh();

        assertThat(requestedUris).containsExactly(FEED_URI);
        assertThat(feed.validate(jwt("revoked-token")).hasErrors()).isTrue();
        assertThat(feed.validate(jwt("other-token")).hasErrors()).isFalse();
    }

    @Test
    void knownRevocationSurvivesAnEmptyOrFailingFeedUntilItExpires() {
        feed.remember(List.of(new RevokedToken("revoked-token", NOW.plus(Duration.ofMinutes(31)))));

        kyc = respondWith("[]");
        feed.refresh();
        assertThat(feed.validate(jwt("revoked-token")).hasErrors()).isTrue();

        kyc = request -> Mono.error(new IllegalStateException("connection refused"));
        feed.refresh();
        assertThat(feed.validate(jwt("revoked-token")).hasErrors()).isTrue();

        clock.now = NOW.plus(Duration.ofMinutes(31));
        feed.refresh();
        assertThat(feed.validate(jwt("revoked-token")).hasErrors()).isFalse();
    }

    @Test
    void tokenWithoutAnIdIsNeverTreatedAsRevoked() {
        feed.remember(List.of(new RevokedToken("revoked-token", NOW.plus(Duration.ofMinutes(31)))));

        Jwt withoutId = Jwt.withTokenValue("token").header("alg", "none").claim("uid", "someone").build();

        assertThat(feed.validate(withoutId).hasErrors()).isFalse();
    }

    private static ExchangeFunction respondWith(String json) {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build());
    }

    private static Jwt jwt(String tokenId) {
        return Jwt.withTokenValue("token").header("alg", "none").jti(tokenId).build();
    }

    private static final class MutableClock extends Clock {
        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
