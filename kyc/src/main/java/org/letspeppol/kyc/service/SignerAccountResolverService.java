package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.FinalizeSigningRequest;
import org.letspeppol.kyc.exception.ForbiddenException;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignerAccountResolverService {

    public record SignerResolution(Account account, AccountType requestedType) {}

    private final JwtClaimExtractor jwtClaimExtractor;
    private final ActivationService activationService;
    private final AccountService accountService;
    private final OwnershipService ownershipService;

    public SignerResolution resolveSignerAccount(FinalizeSigningRequest signingRequest, String fullName) {
        if (signingRequest.email() == null || signingRequest.email().isBlank()) {
            return resolveAuthenticatedAdmin(signingRequest);
        }

        EmailVerification emailVerification = activationService.getPendingVerification(signingRequest.email(), signingRequest.peppolId());
        Account account = accountService.findByEmail(emailVerification.getEmail()).orElseGet(() -> accountService.createPendingAccount(emailVerification.getEmail(), fullName));
        return new SignerResolution(account, emailVerification.getType());
    }

    private SignerResolution resolveAuthenticatedAdmin(FinalizeSigningRequest signingRequest) {
        JwtInfo jwtInfo = jwtClaimExtractor.extract();
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        ownershipService.verifyPeppolIdNotRegistered(signingRequest.peppolId());
        return new SignerResolution(accountService.getByExternalId(jwtInfo.uid()), AccountType.ADMIN);
    }
}
