package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

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
    void attemptsAreAllowedUpToTheLimitThenLocked() {
        LoginAttemptService service = new LoginAttemptService(3, 900, 1_000, Clock.systemUTC());

        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isFalse();
    }

    @Test
    void recordSuccessResetsTheCounter() {
        LoginAttemptService service = new LoginAttemptService(3, 900, 1_000, Clock.systemUTC());
        service.tryAttempt(KEY);
        service.tryAttempt(KEY);
        service.recordSuccess(KEY);

        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isFalse();
    }

    @Test
    void lockExpiresAfterLockoutWindow() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(3, 900, 1_000, clock);
        service.tryAttempt(KEY);
        service.tryAttempt(KEY);
        service.tryAttempt(KEY);
        assertThat(service.tryAttempt(KEY)).isFalse();

        clock.advanceSeconds(901);
        assertThat(service.tryAttempt(KEY)).isTrue();
    }

    @Test
    void attemptsOutsideTheWindowNoLongerCount() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(3, 900, 1_000, clock);
        service.tryAttempt(KEY);
        service.tryAttempt(KEY);

        clock.advanceSeconds(901);

        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isTrue();
        assertThat(service.tryAttempt(KEY)).isFalse();
    }

    @Test
    void cleanupRemovesKeysBelowTheLimit() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(5, 900, 10_000, clock);
        for (int i = 0; i < 1_000; i++) {
            service.tryAttempt("login:nobody-" + i + "@example.com");
        }

        clock.advanceSeconds(901);
        service.cleanup();

        assertThat(service.trackedKeys()).isZero();
    }

    @Test
    void trackedKeysStayBoundedWithoutReleasingLockedKeys() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(2, 900, 10, clock);
        service.tryAttempt(KEY);
        service.tryAttempt(KEY);

        for (int i = 0; i < 100; i++) {
            clock.advanceSeconds(1);
            service.tryAttempt("login:nobody-" + i + "@example.com");
        }

        assertThat(service.trackedKeys()).isLessThanOrEqualTo(10);
        assertThat(service.tryAttempt(KEY)).isFalse();
    }

    @Test
    void concurrentAttemptsCannotExceedTheLimit() throws Exception {
        LoginAttemptService service = new LoginAttemptService(5, 900, 1_000, Clock.systemUTC());
        int threads = 32;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return service.tryAttempt(KEY);
                }));
            }
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) allowed++;
            }
            assertThat(allowed).isEqualTo(5);
        }
    }
}
