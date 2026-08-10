package org.letspeppol.kyc.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.letspeppol.kyc.config.AccountUserDetails;
import org.letspeppol.kyc.config.BrowserAuthenticationSupport;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.dto.AuthSessionResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrowserAuthController {

    @GetMapping("/auth/session")
    public ResponseEntity<AuthSessionResponse> session(CsrfToken csrfToken,
                                                       Authentication authentication,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        BrowserAuthenticationSupport.preventCaching(response);
        HttpSession session = request.getSession(false);
        String status;
        if (session != null
                && session.getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID) != null) {
            status = BrowserAuthenticationSupport.STATUS_TOTP_REQUIRED;
        } else if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountUserDetails) {
            status = BrowserAuthenticationSupport.STATUS_AUTHENTICATED;
        } else {
            status = BrowserAuthenticationSupport.STATUS_ANONYMOUS;
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new AuthSessionResponse(
                        status,
                        csrfToken.getToken(),
                        csrfToken.getHeaderName(),
                        csrfToken.getParameterName()));
    }
}
