package org.letspeppol.app.util;

import org.letspeppol.app.config.SecurityConfig;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.SecurityException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class JwtUtil {

    public static String getPeppolId(Jwt jwt) {
        String peppolId = jwt.getClaim(SecurityConfig.PEPPOL_ID);
        if (peppolId == null || peppolId.isBlank()) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_NOT_PRESENT);
        }
        return peppolId;
    }

    public static UUID getUid(Jwt jwt) {
        String uid = jwt.getClaim(SecurityConfig.UID);
        if (uid == null || uid.isBlank()) {
            throw new SecurityException(AppErrorCodes.JWT_UID_NOT_PRESENT);
        }
        return UUID.fromString(uid);
    }

    public static boolean isPeppolActive(Jwt jwt) {
        Boolean peppolActive = jwt.getClaim(SecurityConfig.PEPPOL_ACTIVE);
        if (peppolActive == null) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ACTIVE_NOT_PRESENT);
        }
        return peppolActive;
    }

}
