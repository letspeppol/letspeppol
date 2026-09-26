package org.letspeppol.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtValidationSupportTest {

    private static final String AUDIENCE = "letspeppol-api";
    private static final String ISSUER = "https://example.org/kyc";

    @Test
    void requiresConfiguredValidIssuer() {
        assertThatThrownBy(() -> JwtValidationSupport.build(AUDIENCE, ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtValidationSupport.build(AUDIENCE, "not a URL"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsOnlyMatchingIssuer() {
        OAuth2TokenValidator<Jwt> validator = JwtValidationSupport.build(AUDIENCE, ISSUER);

        assertThat(validator.validate(jwt(ISSUER)).hasErrors()).isFalse();
        assertThat(validator.validate(jwt(null)).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("https://evil.example")).hasErrors()).isTrue();
    }

    @Test
    void loopbackJwksRequiresExplicitOptInUnderPostgresProfile() {
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("postgres")).thenReturn(true);
        String uri = "http://localhost:8084/auth/oauth2/jwks";

        assertThatThrownBy(() -> SecurityConfig.requireTrustworthyJwkSetUri(uri, environment, false))
                .isInstanceOf(IllegalStateException.class);
        SecurityConfig.requireTrustworthyJwkSetUri(uri, environment, true);
    }

    private static Jwt jwt(String issuer) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("user")
                .audience(List.of(AUDIENCE));
        if (issuer != null) {
            jwt.issuer(issuer);
        }
        return jwt.build();
    }
}
