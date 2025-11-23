package org.letspeppol.app.util;

import org.letspeppol.app.config.SecurityConfig;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.SecurityException;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtUtil {

    public static String getPeppolId(Jwt jwt) {
        String peppolId = jwt.getClaim(SecurityConfig.PEPPOL_ID);
        if (peppolId == null || peppolId.isBlank()) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_NOT_PRESENT);
        }
        return peppolId;
    }

}
