package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.FinalizeSigningRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.model.kbo.Director;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignerAccountResolverService {

    public record SignerResolution(Account account, AccountType requestedType) {}

    private final JwtService jwtService;
    private final ActivationService activationService;
    private final AccountService accountService;

    public SignerResolution resolveSignerAccount(FinalizeSigningRequest signingRequest, String authHeader, String name) {
        if (authHeader != null && !authHeader.isBlank()) {
            var jwtInfo = jwtService.validateAndGetInfo(authHeader);
            if (jwtInfo.accountType() != AccountType.AFFILIATE) {
                Account account = accountService.getByExternalId(jwtInfo.uid());
                if (signingRequest.email() != null && !signingRequest.email().equalsIgnoreCase(account.getEmail())) {
                    throw new KycException(KycErrorCodes.REQUESTER_NOT_VALID);
                }
                return new SignerResolution(account, AccountType.ADMIN);
            }
        }

        if (signingRequest.email() == null || signingRequest.email().isBlank()) {
            throw new KycException(KycErrorCodes.ACCOUNT_NOT_FOUND);
        }

        EmailVerification emailVerification = activationService.getPendingVerification(signingRequest.email(), signingRequest.peppolId());
        Account account = accountService.findByEmail(emailVerification.getEmail()).orElseGet(() -> accountService.createPendingAccount(emailVerification.getEmail(), name));
        return new SignerResolution(account, emailVerification.getType());
    }
}
