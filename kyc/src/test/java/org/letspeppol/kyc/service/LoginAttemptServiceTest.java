package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    /** Test clock that can be advanced to drive lockout expiry deterministically. */
    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-06-22T10:00:00Z");

        @Override public Instant instant() { return instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
    }

    private static final String KEY = "login:user@example.com";

    @Test
    void notBlockedBeforeReachingMaxAttempts() {
        LoginAttemptService service = new LoginAttemptService(3, 900, Clock.systemUTC());
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    void blockedAfterMaxAttempts() {
        LoginAttemptService service = new LoginAttemptService(3, 900, Clock.systemUTC());
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThat(service.isBlocked(KEY)).isTrue();
    }

    @Test
    void recordSuccessResetsTheCounter() {
        LoginAttemptService service = new LoginAttemptService(3, 900, Clock.systemUTC());
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordSuccess(KEY);

        // Two fresh failures should not lock since the counter was reset.
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    void unknownKeyIsNotBlocked() {
        LoginAttemptService service = new LoginAttemptService(3, 900, Clock.systemUTC());
        assertThat(service.isBlocked("never-seen")).isFalse();
    }

    @Test
    void lockExpiresAfterLockoutWindow() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(3, 900, clock);
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        assertThat(service.isBlocked(KEY)).isTrue();

        clock.advanceSeconds(901);
        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    void cleanupRemovesExpiredLocks() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(1, 900, clock);
        service.recordFailure(KEY); // maxAttempts=1 -> immediately locked

        clock.advanceSeconds(901);
        service.cleanup();

        // After cleanup the key is gone; a single new failure must not be already-locked.
        assertThat(service.isBlocked(KEY)).isFalse();
    }
}
