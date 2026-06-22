package org.letspeppol.kyc.config;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.service.LoginAttemptService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Feeds form-login (username/password) outcomes into {@link LoginAttemptService} so repeated
 * bad-credential attempts lock the account for a cooldown window. Only username/password
 * authentications are tracked — bearer/JWT resource-server authentications are ignored.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptEventListener {

    private final LoginAttemptService loginAttemptService;

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        if (isPasswordLogin(event.getAuthentication())) {
            loginAttemptService.recordFailure(AccountUserDetailsService.loginKey(event.getAuthentication().getName()));
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (isPasswordLogin(event.getAuthentication())) {
            loginAttemptService.recordSuccess(AccountUserDetailsService.loginKey(event.getAuthentication().getName()));
        }
    }

    private boolean isPasswordLogin(Authentication authentication) {
        return authentication instanceof UsernamePasswordAuthenticationToken;
    }
}
