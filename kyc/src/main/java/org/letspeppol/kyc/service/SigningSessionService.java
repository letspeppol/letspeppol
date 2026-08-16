package org.letspeppol.kyc.service;

import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived capability store for the public contract-signing flow. Only a SHA-256 hash of the
 * capability is retained; the plaintext token exists only in the prepare response and UI memory.
 */
@Service
public class SigningSessionService {

    public static final String HEADER_NAME = "X-Signing-Session";
    private static final int TOKEN_BYTES = 32;

    record SigningSession(
            String peppolId,
            Long directorId,
            String certificateFingerprint,
            String hashToSign,
            String hashToFinalize,
            boolean finalizable,
            Instant createdAt,
            Instant expiresAt
    ) {}

    record CreatedSigningSession(String token, Instant expiresAt) {}

    private final ConcurrentHashMap<String, SigningSession> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public SigningSessionService(@Value("${signing.session.ttl-seconds:600}") long ttlSeconds) {
        this(Duration.ofSeconds(ttlSeconds), Clock.systemUTC(), new SecureRandom());
    }

    SigningSessionService(Duration ttl, Clock clock, SecureRandom secureRandom) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Signing session TTL must be positive");
        }
        this.ttl = ttl;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    CreatedSigningSession create(
            String peppolId,
            Long directorId,
            String certificateFingerprint,
            String hashToSign,
            String hashToFinalize
    ) {
        return create(peppolId, directorId, certificateFingerprint, hashToSign, hashToFinalize, true);
    }

    CreatedSigningSession createPreview(String peppolId, Long directorId, String hashToFinalize) {
        return create(peppolId, directorId, null, null, hashToFinalize, false);
    }

    private CreatedSigningSession create(
            String peppolId,
            Long directorId,
            String certificateFingerprint,
            String hashToSign,
            String hashToFinalize,
            boolean finalizable
    ) {
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(ttl);
        SigningSession session = new SigningSession(
                peppolId, directorId, certificateFingerprint, hashToSign, hashToFinalize,
                finalizable, createdAt, expiresAt);
        while (true) {
            byte[] tokenBytes = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(tokenBytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            if (sessions.putIfAbsent(hashToken(token), session) == null) {
                return new CreatedSigningSession(token, expiresAt);
            }
        }
    }

    SigningSession requireForContract(String token, String peppolId, Long directorId) {
        SigningSession session = findActive(token);
        if (!constantTimeEquals(session.peppolId(), peppolId)
                || !session.directorId().equals(directorId)) {
            throw notFound();
        }
        return session;
    }

    void requireFinalizable(String token, String peppolId, Long directorId) {
        SigningSession session = requireForContract(token, peppolId, directorId);
        if (!session.finalizable()) {
            throw notFound();
        }
    }

    SigningSession consumeForFinalization(
            String token,
            String peppolId,
            Long directorId,
            String certificateFingerprint,
            String hashToSign,
            String hashToFinalize
    ) {
        String tokenHash = hashTokenOrNotFound(token);
        SigningSession session = sessions.get(tokenHash);
        if (session == null || isExpired(session) || !session.finalizable()
                || !constantTimeEquals(session.peppolId(), peppolId)
                || !session.directorId().equals(directorId)
                || !constantTimeEquals(session.certificateFingerprint(), certificateFingerprint)
                || !constantTimeEquals(session.hashToSign(), hashToSign)
                || !constantTimeEquals(session.hashToFinalize(), hashToFinalize)) {
            throw notFound();
        }
        if (!sessions.remove(tokenHash, session)) {
            throw notFound();
        }
        return session;
    }

    List<SigningSession> removeExpiredSessions() {
        List<SigningSession> removed = new ArrayList<>();
        for (var entry : sessions.entrySet()) {
            SigningSession session = entry.getValue();
            if (isExpired(session) && sessions.remove(entry.getKey(), session)) {
                removed.add(session);
            }
        }
        return removed;
    }

    private SigningSession findActive(String token) {
        String tokenHash = hashTokenOrNotFound(token);
        SigningSession session = sessions.get(tokenHash);
        if (session == null) throw notFound();
        if (isExpired(session)) {
            throw notFound();
        }
        return session;
    }

    private boolean isExpired(SigningSession session) {
        return !clock.instant().isBefore(session.expiresAt());
    }

    private static String hashTokenOrNotFound(String token) {
        if (token == null || token.isBlank() || token.length() > 256) throw notFound();
        return hashToken(token);
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static NotFoundException notFound() {
        return new NotFoundException(KycErrorCodes.CONTRACT_NOT_FOUND);
    }
}
