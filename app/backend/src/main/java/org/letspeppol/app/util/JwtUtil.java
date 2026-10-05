package org.letspeppol.app.util;

import org.letspeppol.app.config.SecurityConfig;
import org.letspeppol.app.dto.AccountType;
import org.letspeppol.app.dto.CompanyPermission;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.SecurityException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
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

    public static Instant getCompanyLastUpdated(Jwt jwt) {
        Object claim = jwt.getClaim(SecurityConfig.COMPANY_LAST_UPDATED);
        if (claim == null) {
            return null;
        }
        if (claim instanceof Instant instant) {
            return instant;
        }
        if (claim instanceof Number epochSeconds) {
            return Instant.ofEpochSecond(epochSeconds.longValue());
        }
        return Instant.parse(claim.toString());
    }

    public static int getPermissionMask(Jwt jwt) {
        if (jwt.getClaim(SecurityConfig.PERMISSION_MASK) instanceof Number mask) {
            return mask.intValue() & CompanyPermission.ALL;
        }
        return CompanyPermission.withoutClaim(jwt.getClaimAsString(SecurityConfig.ACCOUNT_TYPE));
    }

    public static boolean hasPermission(Jwt jwt, CompanyPermission permission) {
        return permission.in(getPermissionMask(jwt));
    }

    public static void requirePermission(Jwt jwt, CompanyPermission permission) {
        if (!hasPermission(jwt, permission)) {
            throw new AccessDeniedException("Missing permission " + permission.name());
        }
    }

    public static boolean isAdmin(Jwt jwt) {
        return AccountType.ADMIN.name().equals(jwt.getClaimAsString(SecurityConfig.ACCOUNT_TYPE));
    }

}
