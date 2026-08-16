package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.exception.NotFoundException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigningSessionServiceTest {

    private static final String COMPANY = "0208:1234567890";
    private static final Long DIRECTOR = 42L;
    private static final String FINGERPRINT = "certificate-fingerprint";
    private static final String DIGEST = "prepared-digest";
    private static final String FILE_ID = "prepared-file-id";

    @Test
    void validSessionCanReadOnlyItsBoundContractAndFinalizationIsSingleUse() {
        MutableClock clock = new MutableClock();
        SigningSessionService service = service(clock);
        String token = create(service);

        assertThat(service.requireForContract(token, COMPANY, DIRECTOR).hashToFinalize())
                .isEqualTo(FILE_ID);
        assertThat(service.requireForContract(token, COMPANY, DIRECTOR).hashToSign())
                .isEqualTo(DIGEST);
        service.requireFinalizable(token, COMPANY, DIRECTOR);

        service.consumeForFinalization(token, COMPANY, DIRECTOR, FINGERPRINT, DIGEST, FILE_ID);

        assertNotFound(() -> service.requireForContract(token, COMPANY, DIRECTOR));
        assertNotFound(() -> service.consumeForFinalization(
                token, COMPANY, DIRECTOR, FINGERPRINT, DIGEST, FILE_ID));
    }

    @Test
    void previewSessionCanReadItsBoundContractButCannotBeFinalized() {
        SigningSessionService service = service(new MutableClock());
        String token = service.createPreview(COMPANY, DIRECTOR, FILE_ID).token();

        assertThat(service.requireForContract(token, COMPANY, DIRECTOR).hashToFinalize())
                .isEqualTo(FILE_ID);
        assertThat(service.requireForContract(token, COMPANY, DIRECTOR).finalizable())
                .isFalse();

        assertNotFound(() -> service.requireFinalizable(token, COMPANY, DIRECTOR));
        assertNotFound(() -> service.consumeForFinalization(
                token, COMPANY, DIRECTOR, FINGERPRINT, DIGEST, FILE_ID));

        // A rejected finalization attempt must not consume the read-only preview capability.
        assertThat(service.requireForContract(token, COMPANY, DIRECTOR)).isNotNull();
    }

    @Test
    void companyDirectorCertificateDigestAndFileBindingsCannotBeMixed() {
        SigningSessionService service = service(new MutableClock());
        String token = create(service);

        assertNotFound(() -> service.requireForContract(token, "0208:0000000000", DIRECTOR));
        assertNotFound(() -> service.requireForContract(token, COMPANY, 99L));
        assertNotFound(() -> service.consumeForFinalization(
                token, COMPANY, DIRECTOR, "other-certificate", DIGEST, FILE_ID));
        assertNotFound(() -> service.consumeForFinalization(
                token, COMPANY, DIRECTOR, FINGERPRINT, "other-digest", FILE_ID));
        assertNotFound(() -> service.consumeForFinalization(
                token, COMPANY, DIRECTOR, FINGERPRINT, DIGEST, "other-file"));

        // Failed binding checks do not let a guessed path consume somebody else's valid session.
        assertThat(service.requireForContract(token, COMPANY, DIRECTOR)).isNotNull();
    }

    @Test
    void missingRandomAndExpiredTokensHaveTheSameGenericFailure() {
        MutableClock clock = new MutableClock();
        SigningSessionService service = service(clock);
        String token = create(service);

        assertNotFound(() -> service.requireForContract(null, COMPANY, DIRECTOR));
        assertNotFound(() -> service.requireForContract("random-unknown-token", COMPANY, DIRECTOR));
        assertNotFound(() -> service.consumeForFinalization(
                null, COMPANY, DIRECTOR, FINGERPRINT, DIGEST, FILE_ID));
        assertNotFound(() -> service.consumeForFinalization(
                "random-unknown-token", COMPANY, DIRECTOR, FINGERPRINT, DIGEST, FILE_ID));

        clock.advance(Duration.ofMinutes(10));
        assertNotFound(() -> service.requireForContract(token, COMPANY, DIRECTOR));
        assertNotFound(() -> service.consumeForFinalization(
                token, COMPANY, DIRECTOR, FINGERPRINT, DIGEST, FILE_ID));
        assertThat(service.removeExpiredSessions())
                .singleElement()
                .extracting(SigningSessionService.SigningSession::hashToFinalize)
                .isEqualTo(FILE_ID);
        assertNotFound(() -> service.requireForContract(token, COMPANY, DIRECTOR));
    }

    private static SigningSessionService service(Clock clock) {
        return new SigningSessionService(Duration.ofMinutes(10), clock, new SecureRandom());
    }

    private static String create(SigningSessionService service) {
        return service.create(COMPANY, DIRECTOR, FINGERPRINT, DIGEST, FILE_ID).token();
    }

    private static void assertNotFound(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(NotFoundException.class)
                .hasMessage("contract_not_found");
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-15T10:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
