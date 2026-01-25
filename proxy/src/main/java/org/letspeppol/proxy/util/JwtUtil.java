package org.letspeppol.proxy.util;

import org.letspeppol.proxy.config.SecurityConfig;
import org.letspeppol.proxy.exception.SecurityException;
import org.letspeppol.proxy.model.AccountType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class JwtUtil {

    public static AccountType getAccountType(Jwt jwt) {
        AccountType accountType = jwt.getClaim(SecurityConfig.ACCOUNT_TYPE);
        if (accountType == null) {
            throw new SecurityException("Does not contain Account Type");
        }
        return accountType;
    }

    public static String getUserPeppolId(Jwt jwt) {
        AccountType accountType = jwt.getClaim(SecurityConfig.ACCOUNT_TYPE);
        if (!accountType.isUser()) {
            throw new SecurityException("Not a user");
        }
        String peppolId = jwt.getClaim(SecurityConfig.PEPPOL_ID);
        if (peppolId == null || peppolId.isBlank()) {
            throw new SecurityException("Peppol ID not present");
        }
        return peppolId;
    }

    public static UUID getAppUid(Jwt jwt) {
        AccountType accountType = jwt.getClaim(SecurityConfig.ACCOUNT_TYPE);
        if (!accountType.isApp()) {
            throw new SecurityException("Not an app");
        }
        String uid = jwt.getClaim(SecurityConfig.UID);
        if (uid == null || uid.isBlank()) {
            throw new SecurityException("UID not present");
        }
        return UUID.fromString(uid);
    }

}
