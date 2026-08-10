package org.letspeppol.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;

import java.io.IOException;

public class TotpAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String TOTP_PENDING_ACCOUNT_ID = "TOTP_PENDING_ACCOUNT_ID";

    private final ObjectMapper objectMapper;
    private final SavedRequestAwareAuthenticationSuccessHandler delegate;

    public TotpAuthenticationSuccessHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

            // Clear the security context: the user is not fully authenticated until TOTP is verified.
            SecurityContextHolder.clearContext();
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

            if (BrowserAuthenticationSupport.requestsJson(request)) {
                BrowserAuthenticationSupport.clearSavedRequest(request, response);
                BrowserAuthenticationSupport.writeStatus(objectMapper, response, HttpStatus.ACCEPTED,
                        BrowserAuthenticationSupport.STATUS_TOTP_REQUIRED);
                return;
            }

            response.sendRedirect(request.getContextPath() + "/totp-verify");
            return;
        }

        request.getSession(true).removeAttribute(TOTP_PENDING_ACCOUNT_ID);
        if (BrowserAuthenticationSupport.requestsJson(request)) {
            BrowserAuthenticationSupport.clearSavedRequest(request, response);
            BrowserAuthenticationSupport.writeStatus(objectMapper, response, HttpStatus.OK,
                    BrowserAuthenticationSupport.STATUS_AUTHENTICATED);
            return;
        }

        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
