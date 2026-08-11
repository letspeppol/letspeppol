package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountUserDetailsTest {

    @Test
    void reconstructedPrincipalFindsTheExistingOidcSession() {
        UUID uid = UUID.randomUUID();
        AccountUserDetails sessionPrincipal = principal("person@example.com", uid);
        AccountUserDetails persistedAuthorizationPrincipal = principal("person@example.com", uid);
        SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
        sessionRegistry.registerNewSession("session-id", sessionPrincipal);

        assertThat(persistedAuthorizationPrincipal).isEqualTo(sessionPrincipal);
        assertThat(persistedAuthorizationPrincipal).hasSameHashCodeAs(sessionPrincipal);
        assertThat(sessionRegistry.getAllSessions(persistedAuthorizationPrincipal, false))
                .extracting(session -> session.getSessionId())
                .containsExactly("session-id");
    }

    @Test
    void differentAccountsRemainDifferentPrincipals() {
        assertThat(principal("person@example.com", UUID.randomUUID()))
                .isNotEqualTo(principal("person@example.com", UUID.randomUUID()));
    }

    private static AccountUserDetails principal(String username, UUID uid) {
        return new AccountUserDetails(username, "password", uid, false, 1L, false, true);
    }
}
