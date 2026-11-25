package org.letspeppol.kyc.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.ActivationService;
import org.letspeppol.kyc.service.CompanyService;
import org.letspeppol.kyc.service.OwnershipService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

import static org.letspeppol.kyc.model.AccountType.ADMIN;
import static org.letspeppol.kyc.model.AccountType.AFFILIATE;

@RestController
@Profile("dev & web-eid-testing & !prod & !production")
@ConditionalOnProperty(name = "server.address", havingValue = "127.0.0.1")
@RequestMapping("/api/register/web-eid-test-bypass")
@RequiredArgsConstructor
public class WebEidTestRegistrationController {
    private final ActivationService activationService;
    private final AccountService accountService;
    private final CompanyService companyService;
    private final OwnershipService ownershipService;
    private final EntityManager entityManager;

    public record Request(String token, String newPassword, Long directorId) {}

    @GetMapping
    public ResponseEntity<Void> available() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Void> complete(@RequestBody Request request) {
        var verification = activationService.getValidTokenInformation(request.token());
        if (verification.getType() != ADMIN && verification.getType() != AFFILIATE) {
            throw new KycException(KycErrorCodes.REQUESTER_NOT_VALID);
        }
        var company = companyService.getByPeppolId(verification.getPeppolId());
        var email = verification.getEmail();
        var name = request.directorId() == null && company.getDirectors().isEmpty() ? email
                : company.getDirectors().stream()
                        .filter(director -> director.getId().equals(request.directorId()))
                        .findFirst().orElseThrow(() -> new KycException(KycErrorCodes.REQUESTER_NOT_VALID)).getName();

        // Consume the exact pending link once, rolling back if account activation fails.
        if (entityManager.createQuery("delete from EmailVerification e where e.id = :id and e.verified = false and e.expiresOn > :now")
                .setParameter("id", verification.getId()).setParameter("now", Instant.now()).executeUpdate() != 1) {
            throw new KycException(KycErrorCodes.TOKEN_NOT_FOUND);
        }
        entityManager.lock(company, LockModeType.PESSIMISTIC_WRITE);
        if (ownershipService.findByAccountEmailAndPeppolIdAndType(email, company.getPeppolId(), ADMIN).isEmpty()) {
            ownershipService.verifyPeppolIdNotRegistered(company.getPeppolId());
        }
        var account = accountService.findByEmail(email).orElseGet(() -> accountService.createPendingAccount(email, name));
        if (!account.isVerified()) {
            accountService.updatePassword(account, request.newPassword());
            accountService.verify(account);
        }
        ownershipService.ensureAdminOwnership(account, company);
        if (verification.getType() == AFFILIATE) {
            activationService.linkAffiliateOwnership(verification);
        }
        return ResponseEntity.noContent().build();
    }
}
