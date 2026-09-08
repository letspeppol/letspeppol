package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The authorization store persists the authenticated principal and reads it back when the
 * authorization code is exchanged. Serialising through the getters instead of the fields loses
 * {@code locked} and {@code verified}, which silently unlocks a locked account.
 */
@SpringBootTest
class AccountUserDetailsSerializationTest {

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    private AccountUserDetails roundTrip(AccountUserDetails principal) {
        RegisteredClient client = registeredClientRepository.findByClientId("letspeppol-ui");
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(principal.getUsername())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(java.security.Principal.class.getName(),
                        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()))
                .build();

        authorizationService.save(authorization);
        try {
            OAuth2Authorization stored = authorizationService.findById(authorization.getId());
            assertThat(stored).isNotNull();
            UsernamePasswordAuthenticationToken authentication =
                    stored.getAttribute(java.security.Principal.class.getName());
            return (AccountUserDetails) authentication.getPrincipal();
        } finally {
            authorizationService.remove(authorization);
        }
    }

    private static AccountUserDetails principal(boolean locked, boolean verified) {
        return new AccountUserDetails("user@example.be", "hash", UUID.randomUUID(), true, 7L, locked, verified);
    }

    @Test
    void lockedAccountStaysLockedAcrossTheAuthorizationStore() {
        AccountUserDetails restored = roundTrip(principal(true, true));

        assertThat(restored.isAccountNonLocked()).isFalse();
        assertThat(restored.isEnabled()).isTrue();
    }

    @Test
    void unverifiedAccountStaysDisabledAcrossTheAuthorizationStore() {
        AccountUserDetails restored = roundTrip(principal(false, false));

        assertThat(restored.isAccountNonLocked()).isTrue();
        assertThat(restored.isEnabled()).isFalse();
    }

    @Test
    void identifyingClaimsSurviveTheAuthorizationStore() {
        AccountUserDetails original = principal(false, true);

        AccountUserDetails restored = roundTrip(original);

        assertThat(restored.getUid()).isEqualTo(original.getUid());
        assertThat(restored.getAccountId()).isEqualTo(original.getAccountId());
        assertThat(restored.getUsername()).isEqualTo(original.getUsername());
        assertThat(restored.isTotpEnabled()).isEqualTo(original.isTotpEnabled());
    }
}
