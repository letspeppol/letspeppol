package org.letspeppol.kyc.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChallengeStore {

    private static final long TTL_SECONDS = 300; // 5 minutes

    private record Entry(byte[] challenge, Instant createdAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String createChallenge() {
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(generateToken());
        store.put(token, new Entry(challenge, Instant.now()));
        return token;
    }

    public byte[] getChallenge(String token) {
        Entry entry = store.get(token);
        if (entry == null) return null;
        return entry.challenge;
    }

    public byte[] consumeChallenge(String token) {
        Entry entry = store.remove(token);
        if (entry == null) return null;
        if (entry.createdAt.plusSeconds(TTL_SECONDS).isBefore(Instant.now())) return null;
        return entry.challenge;
    }

    public String getChallengeBase64Url(String token) {
        byte[] challenge = getChallenge(token);
        if (challenge == null) return null;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(TTL_SECONDS);
        store.entrySet().removeIf(e -> e.getValue().createdAt.isBefore(cutoff));
    }

    private byte[] generateToken() {
        byte[] token = new byte[24];
        random.nextBytes(token);
        return token;
    }
}
