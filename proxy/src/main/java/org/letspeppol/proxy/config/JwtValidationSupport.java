package org.letspeppol.proxy.config;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the JWT validator chain for the resource server: timestamps, required audience, and issuer.
 * Audience binding ensures a token minted for the platform APIs cannot
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
        validators.add(new JwtIssuerValidator(requireValidIssuer(issuer)));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    static String requireValidIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("Issuer URI must be configured");
        }
        try {
            URI uri = new URI(issuer);
            boolean allowedScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost()));
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !allowedScheme
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException(
                        "Issuer URI must be absolute HTTPS (or loopback HTTP for local use) "
                                + "without user info, query, or fragment: " + issuer);
            }
            return uri.toString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid issuer URI: " + issuer, exception);
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "Required audience '" + audience + "' is missing", null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
