package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.dto.FinalizeSigningRequest;
import org.letspeppol.kyc.exception.ForbiddenException;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.letspeppol.kyc.service.jwt.JwtInfo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SignerAccountResolverServiceTest {

    private static final String PEPPOL_ID = "0208:0123456789";
    private static final String OTHER_PEPPOL_ID = "0208:9876543210";
    private static final UUID UID = UUID.randomUUID();

    private final JwtClaimExtractor jwtClaimExtractor = mock(JwtClaimExtractor.class);
    private final ActivationService activationService = mock(ActivationService.class);
    private final AccountService accountService = mock(AccountService.class);
    private final OwnershipService ownershipService = mock(OwnershipService.class);
    private final SignerAccountResolverService resolver =
            new SignerAccountResolverService(jwtClaimExtractor, activationService, accountService, ownershipService);

    @Test
    void addingACompanyRequiresAnAdminToken() {
        when(jwtClaimExtractor.extract()).thenReturn(new JwtInfo(AccountType.USER, OTHER_PEPPOL_ID, true, UID));

        assertThatThrownBy(() -> resolver.resolveSignerAccount(request(null), "Jan Peeters"))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(ownershipService, accountService);
    }

    @Test
    void addingACompanyThatAlreadyHasAnAdminIsRefused() {
        when(jwtClaimExtractor.extract()).thenReturn(new JwtInfo(AccountType.ADMIN, OTHER_PEPPOL_ID, true, UID));
        doThrow(new KycException(KycErrorCodes.COMPANY_ALREADY_REGISTERED))
                .when(ownershipService).verifyPeppolIdNotRegistered(PEPPOL_ID);

        assertThatThrownBy(() -> resolver.resolveSignerAccount(request(null), "Jan Peeters"))
                .isInstanceOf(KycException.class);
        verify(accountService, never()).getByExternalId(any());
    }

    @Test
    void adminAddsACompanyWithoutAnAdmin() {
        Account account = new Account();
        when(jwtClaimExtractor.extract()).thenReturn(new JwtInfo(AccountType.ADMIN, OTHER_PEPPOL_ID, true, UID));
        when(accountService.getByExternalId(UID)).thenReturn(account);

        SignerAccountResolverService.SignerResolution resolution = resolver.resolveSignerAccount(request(null), "Jan Peeters");

        assertThat(resolution.account()).isSameAs(account);
        assertThat(resolution.requestedType()).isEqualTo(AccountType.ADMIN);
    }

    @Test
    void invitedSignerUsesTheInvitationInsteadOfAnyToken() {
        Account account = new Account();
        EmailVerification invitation = new EmailVerification(null, AccountType.AFFILIATE, "jan@example.com", PEPPOL_ID, "token", Instant.now().plusSeconds(60));
        when(activationService.getPendingVerification("jan@example.com", PEPPOL_ID)).thenReturn(invitation);
        when(accountService.findByEmail("jan@example.com")).thenReturn(Optional.of(account));

        SignerAccountResolverService.SignerResolution resolution = resolver.resolveSignerAccount(request("jan@example.com"), "Jan Peeters");

        assertThat(resolution.account()).isSameAs(account);
        assertThat(resolution.requestedType()).isEqualTo(AccountType.AFFILIATE);
        verifyNoInteractions(jwtClaimExtractor);
    }

    private static FinalizeSigningRequest request(String email) {
        return new FinalizeSigningRequest(PEPPOL_ID, 1L, email, "certificate", "signature", null, "hash", "finalize");
    }
}
