package org.letspeppol.app.config;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the JWT validator chain for the resource server: timestamps, required audience, and
 * (when configured) issuer. Audience binding ensures a token minted for the platform APIs cannot
 * be replayed against an unrelated audience.
 */
public final class JwtValidationSupport {

    private JwtValidationSupport() {}

    public static OAuth2TokenValidator<Jwt> build(String audience, String issuer) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (audience != null && !audience.isBlank()) {
            validators.add(audienceValidator(audience));
        }
        if (issuer != null && !issuer.isBlank()) {
            validators.add(new JwtIssuerValidator(issuer));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "Required audience '" + audience + "' is missing", null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
