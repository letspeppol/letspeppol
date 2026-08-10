package org.letspeppol.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

public class JsonAwareAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;
    private final AuthenticationFailureHandler redirectDelegate =
            new SimpleUrlAuthenticationFailureHandler("/login?error");

    public JsonAwareAuthenticationFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID);
        }

        if (!BrowserAuthenticationSupport.requestsJson(request)) {
            redirectDelegate.onAuthenticationFailure(request, response, exception);
            return;
        }

        if (exception instanceof LockedException) {
            BrowserAuthenticationSupport.writeError(objectMapper, response, HttpStatus.TOO_MANY_REQUESTS,
                    KycErrorCodes.TOO_MANY_REQUESTS);
            return;
        }

        BrowserAuthenticationSupport.writeError(objectMapper, response, HttpStatus.UNAUTHORIZED,
                KycErrorCodes.AUTHENTCATION_FAILED);
    }
}
