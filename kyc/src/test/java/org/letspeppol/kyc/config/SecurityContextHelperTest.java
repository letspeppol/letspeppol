package org.letspeppol.kyc.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.Account;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextHelperTest {

    private static final String ISSUER = "http://localhost:8084";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passkeySessionIsIssuedAnIdTokenWithAuthTime() throws Exception {
        Authentication authentication = establishSession(FactorGrantedAuthority.WEBAUTHN_AUTHORITY);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
        assertThat(idToken(authentication).getClaimAsInstant(IdTokenClaimNames.AUTH_TIME)).isNotNull();
    }

    @Test
    void totpSessionIsIssuedAnIdTokenWithAuthTime() throws Exception {
        Authentication authentication = establishSession(
                FactorGrantedAuthority.PASSWORD_AUTHORITY, SecurityContextHelper.TOTP_AUTHORITY);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(FactorGrantedAuthority.PASSWORD_AUTHORITY, SecurityContextHelper.TOTP_AUTHORITY);
        assertThat(idToken(authentication).getClaimAsInstant(IdTokenClaimNames.AUTH_TIME)).isNotNull();
    }

    @Test
    void sessionWithoutAuthenticationFactorIsRefused() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> SecurityContextHelper.establishSession(account(), request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(request.getSession(false)).isNull();
    }

    private static Authentication establishSession(String... factorAuthorities) {
        SecurityContextHelper.establishSession(account(), new MockHttpServletRequest(), factorAuthorities);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static Account account() {
        return Account.builder()
                .id(42L)
                .externalId(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .verified(true)
                .build();
    }

    private static Jwt idToken(Authentication authentication) throws Exception {
        RegisteredClient client = RegisteredClient.withId("ui")
                .clientId("letspeppol-ui")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:9000/callback")
                .scope(OidcScopes.OPENID)
                .build();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(ISSUER + "/auth/oauth2/authorize")
                .clientId(client.getClientId())
                .build();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName(authentication.getName())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
                .build();
        OAuth2TokenContext context = DefaultOAuth2TokenContext.builder()
                .registeredClient(client)
                .principal(authentication)
                .authorizationServerContext(authorizationServerContext())
                .authorization(authorization)
                .tokenType(new OAuth2TokenType(OidcParameterNames.ID_TOKEN))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .put(SessionInformation.class,
                        new SessionInformation(authentication.getPrincipal(), "session-id", new Date()))
                .build();
        return new JwtGenerator(jwtEncoder()).generate(context);
    }

    private static AuthorizationServerContext authorizationServerContext() {
        AuthorizationServerSettings settings = AuthorizationServerSettings.builder().issuer(ISSUER).build();
        return new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return ISSUER;
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return settings;
            }
        };
    }

    private static NimbusJwtEncoder jwtEncoder() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey(keyPair.getPrivate())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }
}
