package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidationSupportTest {

    private static final String AUDIENCE = "letspeppol-api";
    private static final String ISSUER = "https://login.letspeppol.org";

    private static Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("user");
    }

    @Test
    void acceptsTokenWithRequiredAudience() {
        OAuth2TokenValidator<Jwt> validator = JwtValidationSupport.build(AUDIENCE, "");
        Jwt jwt = baseJwt().audience(List.of(AUDIENCE)).build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        OAuth2TokenValidator<Jwt> validator = JwtValidationSupport.build(AUDIENCE, "");
        Jwt jwt = baseJwt().audience(List.of("some-other-api")).build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithNoAudienceWhenAudienceRequired() {
        OAuth2TokenValidator<Jwt> validator = JwtValidationSupport.build(AUDIENCE, "");
        Jwt jwt = baseJwt().build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void skipsAudienceCheckWhenNotConfigured() {
        OAuth2TokenValidator<Jwt> validator = JwtValidationSupport.build("", "");
        Jwt jwt = baseJwt().build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        OAuth2TokenValidator<Jwt> validator = JwtValidationSupport.build("", "");
        Jwt jwt = baseJwt()
                .issuedAt(Instant.now().minusSeconds(3600))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void acceptsTokenWithMatchingIssuer() {
        OAuth2TokenValidator<Jwt> validator = JwtValidationSupport.build(AUDIENCE, ISSUER);
        Jwt jwt = baseJwt().audience(List.of(AUDIENCE)).issuer(ISSUER).build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        OAuth2TokenValidator<Jwt> validator = JwtValidationSupport.build(AUDIENCE, ISSUER);
        Jwt jwt = baseJwt().audience(List.of(AUDIENCE)).issuer("https://evil.example.com").build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }
}
