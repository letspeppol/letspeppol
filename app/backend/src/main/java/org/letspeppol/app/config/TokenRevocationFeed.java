package org.letspeppol.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TokenRevocationFeed {

    private static final String JWKS_PATH = "/auth/oauth2/jwks";
    private static final String FEED_PATH = "/lapi/revocations";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "The token has been revoked", null);

    private final WebClient webClient;
    private final URI feedUri;
    private final Clock clock;
    private volatile Map<String, Instant> expiryByTokenId = Map.of();
    private boolean reachable = true;

    @Autowired
    public TokenRevocationFeed(
            @Value("${oauth2.revocation-feed.uri:}") String configuredUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        this(WebClient.create(), feedUri(configuredUri, jwkSetUri), Clock.systemUTC());
    }

    TokenRevocationFeed(WebClient webClient, URI feedUri, Clock clock) {
        this.webClient = webClient;
        this.feedUri = feedUri;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${oauth2.revocation-feed.poll-delay-ms:5000}")
    public void refresh() {
        try {
            List<RevokedToken> revokedTokens = webClient.get().uri(feedUri).retrieve()
                    .bodyToFlux(RevokedToken.class)
                    .collectList()
                    .block(TIMEOUT);
            remember(revokedTokens == null ? List.of() : revokedTokens);
            if (!reachable) {
                log.info("Token revocation feed {} is reachable again", feedUri);
                reachable = true;
            }
        } catch (RuntimeException e) {
            remember(List.of());
            if (reachable) {
                log.warn("Token revocation feed {} is unreachable, keeping the {} known revocation(s): {}", feedUri, expiryByTokenId.size(), e.getMessage());
                reachable = false;
            }
        }
    }

    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String tokenId = jwt.getId();
        return tokenId != null && expiryByTokenId.containsKey(tokenId)
                ? OAuth2TokenValidatorResult.failure(REVOKED)
                : OAuth2TokenValidatorResult.success();
    }

    void remember(List<RevokedToken> revokedTokens) {
        Instant now = clock.instant();
        Map<String, Instant> known = new HashMap<>(expiryByTokenId);
        revokedTokens.forEach(revokedToken -> known.put(revokedToken.jti(), revokedToken.expiresAt()));
        known.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
        expiryByTokenId = Map.copyOf(known);
    }

    static URI feedUri(String configuredUri, String jwkSetUri) {
        if (configuredUri != null && !configuredUri.isBlank()) {
            return URI.create(configuredUri);
        }
        if (jwkSetUri.endsWith(JWKS_PATH)) {
            return URI.create(jwkSetUri.substring(0, jwkSetUri.length() - JWKS_PATH.length()) + FEED_PATH);
        }
        return URI.create(jwkSetUri).resolve(FEED_PATH);
    }

    record RevokedToken(String jti, Instant expiresAt) {}
}
