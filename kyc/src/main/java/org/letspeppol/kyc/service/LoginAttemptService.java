package org.letspeppol.kyc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory brute-force throttle for authentication factors (form login and TOTP verification).
 * Keys are opaque strings chosen by the caller (e.g. lower-cased username, or "totp:" + accountId).
 * After {@code maxAttempts} attempts without a success within {@code lockoutSeconds}, a key is locked for
 * {@code lockoutSeconds}.
 *
 * <p>State is per-instance (matching KYC's other in-memory stores) and resets on restart — correct
 * for the current single-active-instance deployment; back it with a shared store if scaled out.
 * Form login is locked by username (a deliberate account-lockout tradeoff, bounded by the lockout
 * window); the TOTP step is keyed by account id, reachable only after a correct password.
 */
@Service
public class LoginAttemptService {

    private static final int DEFAULT_MAX_TRACKED_KEYS = 50_000;

    private record Attempt(int count, Instant windowStart, Instant lockedUntil) {

        boolean isLocked(Instant now) {
            return lockedUntil != null && lockedUntil.isAfter(now);
        }

        boolean isExpired(Instant now, Duration lockout) {
            Instant end = lockedUntil != null ? lockedUntil : windowStart.plus(lockout);
            return !end.isAfter(now);
        }
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final Duration lockout;
    private final int maxTrackedKeys;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            @Value("${auth.lockout.max-attempts:5}") int maxAttempts,
            @Value("${auth.lockout.duration-seconds:900}") long lockoutSeconds) {
        this(maxAttempts, lockoutSeconds, DEFAULT_MAX_TRACKED_KEYS, Clock.systemUTC());
    }

    // Visible for testing: a fixed Clock makes lockout-expiry behaviour deterministic.
    LoginAttemptService(int maxAttempts, long lockoutSeconds, int maxTrackedKeys, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockout = Duration.ofSeconds(lockoutSeconds);
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = clock;
    }

    public boolean tryAttempt(String key) {
        Instant now = Instant.now(clock);
        AtomicBoolean allowed = new AtomicBoolean();
        attempts.compute(key, (k, existing) -> {
            if (existing != null && existing.isLocked(now)) {
                return existing;
            }
            allowed.set(true);
            Attempt current = existing == null || existing.isExpired(now, lockout) ? null : existing;
            int count = current == null ? 1 : current.count() + 1;
            Instant windowStart = current == null ? now : current.windowStart();
            return new Attempt(count, windowStart, count >= maxAttempts ? now.plus(lockout) : null);
        });
        if (attempts.size() > maxTrackedKeys) {
            evictOldest(now);
        }
        return allowed.get();
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    int trackedKeys() {
        return attempts.size();
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        removeExpired(Instant.now(clock));
    }

    private void removeExpired(Instant now) {
        attempts.entrySet().removeIf(e -> e.getValue().isExpired(now, lockout));
    }

    private void evictOldest(Instant now) {
        removeExpired(now);
        int retained = maxTrackedKeys - maxTrackedKeys / 10;
        int excess = attempts.size() - retained;
        if (excess <= 0) {
            return;
        }
        attempts.entrySet().stream()
                .filter(e -> !e.getValue().isLocked(now))
                .sorted(Comparator.comparing(e -> e.getValue().windowStart()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(attempts::remove);
    }
}
