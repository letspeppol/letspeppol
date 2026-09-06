package org.letspeppol.kyc.controller;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.config.AccountUserDetails;
import org.letspeppol.kyc.config.BrowserAuthenticationSupport;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.dto.AuthSessionResponse;
import org.letspeppol.kyc.model.Account;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserAuthControllerTest {

    private final BrowserAuthController controller = new BrowserAuthController();
    private final DefaultCsrfToken csrf = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "csrf-value");

    @Test
    void anonymousSessionReturnsCsrfBootstrap() {
        AuthSessionResponse body = controller.session(
                csrf, null, new MockHttpServletRequest(), new MockHttpServletResponse()).getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(BrowserAuthenticationSupport.STATUS_ANONYMOUS);
        assertThat(body.csrfToken()).isEqualTo("csrf-value");
        assertThat(body.csrfHeaderName()).isEqualTo("X-CSRF-TOKEN");
        assertThat(body.csrfParameterName()).isEqualTo("_csrf");
    }

    @Test
    void pendingTotpTakesPrecedenceOverAuthentication() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID, 42L);

        AuthSessionResponse body = controller.session(
                csrf, sessionAuthentication(), request, new MockHttpServletResponse()).getBody();

        assertThat(body.status()).isEqualTo(BrowserAuthenticationSupport.STATUS_TOTP_REQUIRED);
    }

    @Test
    void accountUserDetailsAuthenticationIsReportedAsAuthenticated() {
        AuthSessionResponse body = controller.session(
                csrf, sessionAuthentication(), new MockHttpServletRequest(),
                new MockHttpServletResponse()).getBody();

        assertThat(body.status()).isEqualTo(BrowserAuthenticationSupport.STATUS_AUTHENTICATED);
    }

    @Test
    void nonSessionPrincipalIsNotReportedAsAuthenticated() {
        TestingAuthenticationToken bearerLikeAuthentication =
                new TestingAuthenticationToken("jwt-principal", null);
        bearerLikeAuthentication.setAuthenticated(true);

        AuthSessionResponse body = controller.session(
                csrf, bearerLikeAuthentication, new MockHttpServletRequest(),
                new MockHttpServletResponse()).getBody();

        assertThat(body.status()).isEqualTo(BrowserAuthenticationSupport.STATUS_ANONYMOUS);
    }

    private UsernamePasswordAuthenticationToken sessionAuthentication() {
        Account account = Account.builder()
                .id(42L)
                .externalId(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .verified(true)
                .build();
        AccountUserDetails principal = new AccountUserDetails(account);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }
}
