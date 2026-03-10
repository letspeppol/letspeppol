package org.letspeppol.kyc.service.jwt;

import org.letspeppol.kyc.model.AccountType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtClaimExtractor {

    public JwtInfo extract() {
        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = authentication.getToken();

        return new JwtInfo(
                AccountType.valueOf(jwt.getClaimAsString("accountType")),
                jwt.getClaimAsString("peppolId"),
                jwt.getClaim("peppolActive"),
                UUID.fromString(jwt.getClaimAsString("uid"))
        );
    }
}
