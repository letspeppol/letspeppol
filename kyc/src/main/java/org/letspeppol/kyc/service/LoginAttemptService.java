package org.letspeppol.kyc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force throttle for authentication factors (form login and TOTP verification).
 * Keys are opaque strings chosen by the caller (e.g. lower-cased username, or "totp:" + accountId).
 * After {@code maxAttempts} consecutive failures a key is locked for {@code lockoutSeconds}.
 *
 * <p>State is per-instance (matching KYC's other in-memory stores) and resets on restart — correct
 * for the current single-active-instance deployment; back it with a shared store if scaled out.
 * Form login is locked by username (a deliberate account-lockout tradeoff, bounded by the lockout
 * window); the TOTP step is keyed by account id, reachable only after a correct password.
 */
@Service
public class LoginAttemptService {

    private record Attempt(int count, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final long lockoutSeconds;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            @Value("${auth.lockout.max-attempts:5}") int maxAttempts,
            @Value("${auth.lockout.duration-seconds:900}") long lockoutSeconds) {
        this(maxAttempts, lockoutSeconds, Clock.systemUTC());
    }

    // Visible for testing: a fixed Clock makes lockout-expiry behaviour deterministic.
    LoginAttemptService(int maxAttempts, long lockoutSeconds, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockoutSeconds = lockoutSeconds;
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.lockedUntil() == null) {
            return false;
        }
        if (attempt.lockedUntil().isAfter(Instant.now(clock))) {
            return true;
        }
        // Lock expired — clear it.
        attempts.remove(key);
        return false;
    }

    public void recordFailure(String key) {
        attempts.compute(key, (k, existing) -> {
            int count = (existing == null ? 0 : existing.count()) + 1;
            Instant lockedUntil = count >= maxAttempts ? Instant.now(clock).plusSeconds(lockoutSeconds) : null;
            return new Attempt(count, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        Instant now = Instant.now(clock);
        attempts.entrySet().removeIf(e ->
                e.getValue().lockedUntil() != null && e.getValue().lockedUntil().isBefore(now));
    }
}
