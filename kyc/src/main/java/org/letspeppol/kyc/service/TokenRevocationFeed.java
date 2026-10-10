package org.letspeppol.kyc.service;

import org.letspeppol.kyc.dto.RevokedToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenRevocationFeed {

    private static final Duration ACCEPTED_CLOCK_SKEW = Duration.ofMinutes(1);
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "The token has been revoked", null);

    private final ConcurrentHashMap<String, Instant> expiryByTokenId = new ConcurrentHashMap<>();
    private final Clock clock;

    public TokenRevocationFeed() {
        this(Clock.systemUTC());
    }

    TokenRevocationFeed(Clock clock) {
        this.clock = clock;
    }

    public void revoke(String tokenId, Instant tokenExpiresAt) {
        dropExpired();
        expiryByTokenId.put(tokenId, tokenExpiresAt.plus(ACCEPTED_CLOCK_SKEW));
    }

    public boolean isRevoked(String tokenId) {
        return tokenId != null && expiryByTokenId.containsKey(tokenId);
    }

    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return isRevoked(jwt.getId())
                ? OAuth2TokenValidatorResult.failure(REVOKED)
                : OAuth2TokenValidatorResult.success();
    }

    public List<RevokedToken> entries() {
        dropExpired();
        return expiryByTokenId.entrySet().stream()
                .map(entry -> new RevokedToken(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void dropExpired() {
        Instant now = clock.instant();
        expiryByTokenId.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
    }
}
