package org.letspeppol.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

import java.security.Principal;
import java.util.Map;
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

    @Test
    void browserPrincipalHasNoApiAuthority() {
        assertThat(principal("person@example.com", UUID.randomUUID()).getAuthorities()).isEmpty();
    }

    @Test
    void storedAuthorizationDoesNotContainThePasswordHash() throws Exception {
        AccountUserDetails principal = new AccountUserDetails("person@example.com", "$2a$12$hash", UUID.randomUUID(), false, 1L, false, true);
        ObjectMapper authorizationMapper = new ObjectMapper();
        authorizationMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        authorizationMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        authorizationMapper.registerModule(new AccountUserDetailsJacksonModule());

        String storedAttributes = authorizationMapper.writeValueAsString(Map.of(
                Principal.class.getName(),
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())));

        assertThat(storedAttributes).contains(principal.getUid().toString()).doesNotContain("$2a$12$hash");
    }

    @Test
    void erasingCredentialsDropsThePasswordHash() {
        AccountUserDetails principal = principal("person@example.com", UUID.randomUUID());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

        authentication.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
    }

    private static AccountUserDetails principal(String username, UUID uid) {
        return new AccountUserDetails(username, "password", uid, false, 1L, false, true);
    }
}
