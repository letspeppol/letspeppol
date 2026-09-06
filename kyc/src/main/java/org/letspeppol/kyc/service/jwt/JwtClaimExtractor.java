package org.letspeppol.kyc.service.jwt;

import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.AccountType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JwtClaimExtractor {

    /**
     * Claims of the acting user's access token, or empty when the request is anonymous or
     * session-authenticated (the /auth/browser/login form) rather than bearer-authenticated. Endpoints under
     * {@code /api/**} are public, so callers there must treat the token as optional.
     */
    public Optional<JwtInfo> extractOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Optional.empty();
        }
        Jwt jwt = jwtAuthentication.getToken();
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) {
            return Optional.empty();
        }
        String accountType = jwt.getClaimAsString("accountType");
        return Optional.of(new JwtInfo(
                accountType != null ? AccountType.valueOf(accountType) : null,
                jwt.getClaimAsString("peppolId"),
                jwt.getClaim("peppolActive"),
                UUID.fromString(uid)
        ));
    }

    public JwtInfo extract() {
        return extractOptional().orElseThrow(() -> new KycException(KycErrorCodes.AUTHENTCATION_FAILED));
    }
}
