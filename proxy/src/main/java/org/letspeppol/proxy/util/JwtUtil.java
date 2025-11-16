package org.letspeppol.proxy.util;

import org.letspeppol.proxy.config.SecurityConfig;
import org.letspeppol.proxy.exception.SecurityException;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtUtil {

    public static String getPeppolId(Jwt jwt) {
        String peppolId = jwt.getClaim(SecurityConfig.PEPPOL_ID);
        if (peppolId == null || peppolId.isBlank()) {
            throw new SecurityException("Peppol ID not present");
        }
        return peppolId;
    }

    public static String getCompanyNumber(Jwt jwt) {
        String peppolId = getPeppolId(jwt);
        String [] parts = peppolId.split(":");
        if (parts.length != 2) {
            throw new SecurityException("Peppol ID not valid");
        }
        return parts[1];
    }

}
