package org.letspeppol.kyc.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.config.BrowserAuthenticationSupport;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.dto.AuthErrorResponse;
import org.letspeppol.kyc.dto.AuthStatusResponse;
import org.letspeppol.kyc.dto.PasskeyAuthenticationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.service.PasskeyService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasskeyControllerBrowserAuthTest {

    private final PasskeyService passkeyService = mock(PasskeyService.class);
    private final PasskeyController controller =
            new PasskeyController(passkeyService, mock(JwtClaimExtractor.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifyWithoutChallengeSessionReturnsGenericUnauthorized() {
        var response = controller.verifyAuthentication(
                credential(), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isEqualTo(new AuthErrorResponse(KycErrorCodes.AUTHENTCATION_FAILED));
        verifyNoInteractions(passkeyService);
    }

    @Test
    void expectedCeremonyFailureReturnsGenericUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        when(passkeyService.verifyAuthentication(credential(), request.getSession()))
                .thenThrow(new IllegalArgumentException("bad signature"));

        var response = controller.verifyAuthentication(
                credential(), request, new MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isEqualTo(new AuthErrorResponse(KycErrorCodes.AUTHENTCATION_FAILED));
    }

    @Test
    void successfulCeremonyEstablishesSessionAndClearsStaleState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        request.getSession().setAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID, 99L);
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.saveRequest(request, servletResponse);

        Account account = Account.builder()
                .id(42L)
                .externalId(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .verified(true)
                .build();
        when(passkeyService.verifyAuthentication(credential(), request.getSession())).thenReturn(account);

        var response = controller.verifyAuthentication(credential(), request, servletResponse);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .isEqualTo(new AuthStatusResponse(BrowserAuthenticationSupport.STATUS_AUTHENTICATED));
        assertThat(request.getSession().getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID))
                .isNull();
        assertThat(requestCache.getRequest(request, servletResponse)).isNull();
        SecurityContext sessionContext = (SecurityContext) request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(sessionContext).isNotNull();
        assertThat(sessionContext.getAuthentication().isAuthenticated()).isTrue();
    }

    private PasskeyAuthenticationResponse credential() {
        return new PasskeyAuthenticationResponse(
                "credential-id", "credential-id", "public-key",
                "client-data", "authenticator-data", "signature", null);
    }
}
