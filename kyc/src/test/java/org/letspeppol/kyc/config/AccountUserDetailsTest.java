package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.jackson.SecurityJacksonModules;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

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
        BasicPolymorphicTypeValidator.Builder typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(AccountUserDetails.class);
        JsonMapper authorizationMapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(), typeValidator))
                .addModule(new AccountUserDetailsJacksonModule())
                .build();

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
