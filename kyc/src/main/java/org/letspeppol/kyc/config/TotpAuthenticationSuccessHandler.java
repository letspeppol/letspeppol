package org.letspeppol.kyc.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.io.IOException;

public class TotpAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String TOTP_PENDING_ACCOUNT_ID = "TOTP_PENDING_ACCOUNT_ID";

    private final SavedRequestAwareAuthenticationSuccessHandler delegate;

    public TotpAuthenticationSuccessHandler() {
        this.delegate = new SavedRequestAwareAuthenticationSuccessHandler();
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        this.delegate.setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof AccountUserDetails userDetails
                && userDetails.isTotpEnabled()) {
            HttpSession session = request.getSession(true);
            session.setAttribute(TOTP_PENDING_ACCOUNT_ID, userDetails.getAccountId());

            // Clear security context — user is NOT fully authenticated until TOTP is verified
            SecurityContextHolder.clearContext();
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

            response.sendRedirect(request.getContextPath() + "/totp-verify");
            return;
        }

        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
