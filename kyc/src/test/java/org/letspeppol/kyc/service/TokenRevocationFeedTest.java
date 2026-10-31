package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.dto.RevokedToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TokenRevocationFeedTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final TokenRevocationFeed feed = new TokenRevocationFeed(clock);

    @Test
    void revokedTokenIsRejectedAndListedUntilItCanNoLongerBeAccepted() {
        Instant tokenExpiry = NOW.plus(Duration.ofMinutes(30));

        feed.revoke("revoked-token", tokenExpiry);

        assertThat(feed.validate(jwt("revoked-token")).hasErrors()).isTrue();
        assertThat(feed.validate(jwt("other-token")).hasErrors()).isFalse();
        assertThat(feed.entries()).containsExactly(new RevokedToken("revoked-token", tokenExpiry.plus(Duration.ofMinutes(1))));
    }

    @Test
    void entryIsDroppedOnceTheTokenHasExpiredBeyondTheAcceptedClockSkew() {
        feed.revoke("revoked-token", NOW.plus(Duration.ofMinutes(30)));

        clock.now = NOW.plus(Duration.ofMinutes(30)).plusSeconds(59);
        assertThat(feed.entries()).hasSize(1);

        clock.now = NOW.plus(Duration.ofMinutes(31));
        assertThat(feed.entries()).isEmpty();
        assertThat(feed.isRevoked("revoked-token")).isFalse();
    }

    @Test
    void tokenWithoutAnIdIsNeverTreatedAsRevoked() {
        feed.revoke("revoked-token", NOW.plus(Duration.ofMinutes(30)));

        Jwt withoutId = Jwt.withTokenValue("token").header("alg", "none").claim("uid", "someone").build();

        assertThat(feed.validate(withoutId).hasErrors()).isFalse();
    }

    private static Jwt jwt(String tokenId) {
        return Jwt.withTokenValue("token").header("alg", "none").jti(tokenId).build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

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
