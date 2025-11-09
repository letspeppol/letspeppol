package io.tubs.app.util;

import io.tubs.app.config.SecurityConfig;
import io.tubs.app.exception.AppErrorCodes;
import io.tubs.app.exception.SecurityException;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtUtil {

    public static String getPeppolId(Jwt jwt) {
        String peppolId = jwt.getClaim(SecurityConfig.PEPPOL_ID);
        if (peppolId == null || peppolId.isBlank()) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_NOT_PRESENT);
        }
        return peppolId;
    }

    public static String getCompanyNumber(Jwt jwt) {
        String peppolId = getPeppolId(jwt);
        String [] parts = peppolId.split(":");
        if (parts.length != 2) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_INVALID);
        }
        return parts[1];
    }

}
