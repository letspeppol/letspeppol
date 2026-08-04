package org.letspeppol.kyc.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory, fixed-window rate limiter for sensitive unauthenticated endpoints
 * (login, activation request, password-reset request). KYC runs as a single instance, so an
 * in-memory limiter is sufficient. It THROTTLES (HTTP 429) rather than locking accounts, and is
 * keyed by client IP + target identifier so an attacker on one network cannot lock a victim out.
 * Limits are configurable; defaults are generous enough not to affect legitimate users.
 */
@Service
@Slf4j
public class RateLimiterService {

    private static final int MAX_TRACKED_KEYS = 50_000;

    @Value("${rate-limit.login.max-attempts:10}")
    private int loginMax;
    @Value("${rate-limit.login.window-seconds:300}")
    private long loginWindowSeconds;
    @Value("${rate-limit.email.max-attempts:5}")
    private int emailMax;
    @Value("${rate-limit.email.window-seconds:3600}")
    private long emailWindowSeconds;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    // Login is keyed by client IP + identifier as defense-in-depth (the X-Forwarded-For source IP is only
    // as trustworthy as the edge proxy; full account-lockout brute-force protection belongs to the auth
    // rewrite). Activation/reset are keyed by EMAIL ONLY so a specific address cannot be e-mail-bombed
    // regardless of the (spoofable) source IP — there is no victim lock-out concern for those flows.
    public void checkLogin(String identifier) {
        check("login", identifier, true, loginMax, Duration.ofSeconds(loginWindowSeconds));
    }

    public void checkActivation(String email) {
        check("activation", email, false, emailMax, Duration.ofSeconds(emailWindowSeconds));
    }

    public void checkPasswordReset(String email) {
        check("password-reset", email, false, emailMax, Duration.ofSeconds(emailWindowSeconds));
    }

    private void check(String bucket, String identifier, boolean includeIp, int maxAttempts, Duration window) {
        final String ip = includeIp ? currentClientIp() : null;
        final String id = identifier == null ? "" : identifier.toLowerCase();
        final String key = includeIp ? bucket + "|" + ip + "|" + id : bucket + "|" + id;
        final long now = System.currentTimeMillis();
        final long windowMs = window.toMillis();
        final int[] count = new int[1];
        windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMs) {
                Window fresh = new Window(now);
                count[0] = fresh.count;
                return fresh;
            }
            existing.count++;
            count[0] = existing.count;
            return existing;
        });
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMs);
        }
        if (count[0] > maxAttempts) {
            log.warn("Rate limit exceeded (bucket={}{})", bucket, includeIp ? ", ip=" + ip : "");
            throw new TooManyRequestsException(KycErrorCodes.TOO_MANY_REQUESTS);
        }
    }

    private static String currentClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "unknown";
        }
        HttpServletRequest req = attrs.getRequest();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String ip = req.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
            this.count = 1;
        }
    }
}
