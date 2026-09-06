package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeStoreTest {

    private final ChallengeStore store = new ChallengeStore();

    @Test
    void createReturnsTokenWithRetrievableChallenge() {
        String token = store.createChallenge();
        assertThat(token).isNotBlank();

        String challengeB64 = store.getChallengeBase64Url(token);
        assertThat(challengeB64).isNotBlank();
        // The encoded challenge is a 32-byte value.
        assertThat(Base64.getUrlDecoder().decode(challengeB64)).hasSize(32);
    }

    @Test
    void consumeReturnsChallengeOnceThenRemovesIt() {
        String token = store.createChallenge();
        String expected = store.getChallengeBase64Url(token);

        byte[] consumed = store.consumeChallenge(token);
        assertThat(consumed).isNotNull();
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(consumed)).isEqualTo(expected);

        // Second consume yields nothing — single use.
        assertThat(store.consumeChallenge(token)).isNull();
    }

    @Test
    void consumeUnknownTokenReturnsNull() {
        assertThat(store.consumeChallenge("does-not-exist")).isNull();
    }

    @Test
    void base64LookupForUnknownTokenReturnsNull() {
        assertThat(store.getChallengeBase64Url("does-not-exist")).isNull();
    }
}
