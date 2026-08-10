package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.dto.PasskeyAuthenticationResponse;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.PasskeyCredentialRepository;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasskeyServiceAuthenticationChallengeTest {

    @Test
    void authenticationChallengeExpiresServerSideAndIsConsumed() {
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("postgres")).thenReturn(false);
        PasskeyService service = new PasskeyService(
                mock(PasskeyCredentialRepository.class),
                mock(AccountRepository.class),
                new ChallengeStore(),
                environment,
                "localhost",
                "Let's Peppol Test",
                "http://localhost");
        MockHttpSession session = new MockHttpSession();
        service.generateAuthenticationOptions(session);
        session.setAttribute("webauthn_challenge_issued_at", Instant.now().minusSeconds(301));

        assertThatThrownBy(() -> service.verifyAuthentication(credential(), session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired challenge");
        assertThat(session.getAttribute("webauthn_challenge")).isNull();
        assertThat(session.getAttribute("webauthn_challenge_issued_at")).isNull();
    }

    private PasskeyAuthenticationResponse credential() {
        return new PasskeyAuthenticationResponse(
                "credential-id", "credential-id", "public-key",
                "client-data", "authenticator-data", "signature", null);
    }
}
