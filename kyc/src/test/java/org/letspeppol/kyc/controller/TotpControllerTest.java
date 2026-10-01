package org.letspeppol.kyc.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.config.BrowserAuthenticationSupport;
import org.letspeppol.kyc.config.SecurityContextHelper;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.dto.AuthStatusResponse;
import org.letspeppol.kyc.dto.TotpVerifyRequest;
import org.letspeppol.kyc.exception.TooManyRequestsException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.service.LoginAttemptService;
import org.letspeppol.kyc.service.TotpService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TotpControllerTest {

    private static final int MAX_ATTEMPTS = 5;

    private final TotpService totpService = mock(TotpService.class);
    private final JwtClaimExtractor jwtClaimExtractor = mock(JwtClaimExtractor.class);
    private final TotpController controller = new TotpController(
            totpService, jwtClaimExtractor, new LoginAttemptService(MAX_ATTEMPTS, 900));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiedLoginEstablishesSessionWithPasswordAndTotpFactors() {
        Account account = Account.builder()
                .id(42L)
                .externalId(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .totpEnabled(true)
                .verified(true)
                .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID, 42L);
        when(totpService.findById(42L)).thenReturn(account);
        when(totpService.verify(account, "123456")).thenReturn(true);

        var response = controller.verifyLogin(
                new TotpVerifyRequest("123456"), request, new MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .isEqualTo(new AuthStatusResponse(BrowserAuthenticationSupport.STATUS_AUTHENTICATED));
        assertThat(request.getSession().getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID))
                .isNull();
        SecurityContext sessionContext = (SecurityContext) request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(sessionContext.getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(FactorGrantedAuthority.PASSWORD_AUTHORITY, SecurityContextHelper.TOTP_AUTHORITY);
    }

    @Test
    void disableIsThrottledAfterRepeatedInvalidCodes() {
        UUID uid = UUID.randomUUID();
        Account account = Account.builder().id(42L).externalId(uid).totpEnabled(true).build();
        when(jwtClaimExtractor.extract()).thenReturn(new JwtInfo(AccountType.ADMIN, "0208:0123456789", true, uid));
        when(totpService.findByExternalId(uid)).thenReturn(account);
        doThrow(new IllegalArgumentException("Invalid TOTP code")).when(totpService).disable(any(), anyString());
        TotpVerifyRequest request = new TotpVerifyRequest("000000");

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> controller.disable(request)).isInstanceOf(IllegalArgumentException.class);
        }

        assertThatThrownBy(() -> controller.disable(request)).isInstanceOf(TooManyRequestsException.class);
        verify(totpService, times(MAX_ATTEMPTS)).disable(account, "000000");
    }
}
