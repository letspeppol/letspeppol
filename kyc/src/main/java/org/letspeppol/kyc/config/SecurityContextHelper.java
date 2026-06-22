package org.letspeppol.kyc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.letspeppol.kyc.model.Account;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

public final class SecurityContextHelper {

    private SecurityContextHelper() {}

    /**
     * Establishes an authenticated session for the given account, rotating the session id first
     * to prevent session-fixation (an attacker who pre-seeded a session id cannot reuse it once
     * the user authenticates via passkey / TOTP).
     */
    public static void establishSession(Account account, HttpServletRequest request) {
        // Rotate the session id on privilege change. Ensure a session exists first.
        request.getSession(true);
        request.changeSessionId();
        establishSession(account, request.getSession(true));
    }

    public static void establishSession(Account account, HttpSession session) {
        AccountUserDetails userDetails = new AccountUserDetails(account);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    }
}
