package org.letspeppol.kyc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.letspeppol.kyc.model.Account;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SecurityContextHelper {

    public static final String TOTP_AUTHORITY = "FACTOR_TOTP";

    private SecurityContextHelper() {}

    /**
     * Establishes an authenticated session for the given account, rotating the session id first
     * to prevent session-fixation (an attacker who pre-seeded a session id cannot reuse it once
     * the user authenticates via passkey / TOTP).
     */
    public static void establishSession(Account account, HttpServletRequest request, String... factorAuthorities) {
        Assert.notEmpty(factorAuthorities, "factorAuthorities cannot be empty");
        // Rotate the session id on privilege change. Ensure a session exists first.
        request.getSession(true);
        request.changeSessionId();
        establishSession(account, request.getSession(true), factorAuthorities);
    }

    // Private: callers must go through the HttpServletRequest overload above so the session id is
    // always rotated. Establishing on an un-rotated session would reopen the session-fixation hole.
    private static void establishSession(Account account, HttpSession session, String... factorAuthorities) {
        AccountUserDetails userDetails = new AccountUserDetails(account);
        List<GrantedAuthority> authorities = new ArrayList<>(userDetails.getAuthorities());
        Arrays.stream(factorAuthorities).map(FactorGrantedAuthority::fromAuthority).forEach(authorities::add);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
        authentication.eraseCredentials();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    }
}
