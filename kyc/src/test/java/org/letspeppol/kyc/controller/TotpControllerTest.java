package org.letspeppol.kyc.controller;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.dto.TotpVerifyRequest;
import org.letspeppol.kyc.exception.TooManyRequestsException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.service.LoginAttemptService;
import org.letspeppol.kyc.service.TotpService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.letspeppol.kyc.service.jwt.JwtInfo;

import java.util.UUID;

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
