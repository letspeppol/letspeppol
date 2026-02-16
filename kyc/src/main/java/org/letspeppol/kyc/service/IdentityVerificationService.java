package org.letspeppol.kyc.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.letspeppol.kyc.dto.IdentityVerificationRequest;
import org.letspeppol.kyc.dto.NewUserRequest;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.DirectorIdentityVerification;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.repository.AccountIdentityVerificationRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.UUID;

import static org.letspeppol.kyc.service.SigningService.isAllowedToSign;
import static org.letspeppol.kyc.service.signing.CertificateUtil.getRDNName;

@Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class IdentityVerificationService {

    private final AccountIdentityVerificationRepository accountIdentityVerificationRepository;
    private final AccountService accountService;
    private final OwnershipService ownershipService;
    private final DirectorRepository directorRepository;
    private final JavaMailSender mailSender;
    private final EncryptionService encryptionService;
    private final CompanyService companyService;
    private final PasswordEncoder passwordEncoder;

    public Account createVerifiedAccount(AccountType accountType, IdentityVerificationRequest req) {
        ownershipService.verifyPeppolIdNotRegistered(req.director().getCompany().getPeppolId()); //TODO : check how do we create other verified accounts ?
//        accountService.verifyEmailNotRegistered(req.email()); //TODO : check for multiple email addresses

        Account account = new Account();
        account.setName(req.director().getName());
        account.setEmail(req.email().toLowerCase());
        account.setIdentityVerified(true);
        account.setIdentityVerifiedOn(Instant.now());
        account.setCreatedOn(Instant.now());
        String passwordHash = passwordEncoder.encode(req.password());
        account.setPasswordHash(passwordHash);
        accountService.create(account);
        if (accountType == AccountType.ACCOUNTANT) { //TODO : do we automatically create ADMIN here ? do we check if this is correct ?
            ownershipService.link(account, AccountType.ADMIN, req.director().getCompany());
        }
        ownershipService.link(account, accountType, req.director().getCompany());

        DirectorIdentityVerification directorIdentityVerification = new DirectorIdentityVerification(
                account,
                req.director(),
                req.director().getName(),
                getCN(req.x509Certificate()),
                encryptionService.encrypt(req.x509Certificate().getSerialNumber().toString()),
                req.algorithm(),
                req.hashToSign(),
                encryptionService.encrypt(req.certificate()),
                encryptionService.encrypt(req.signature())
        );
        accountIdentityVerificationRepository.save(directorIdentityVerification);

        req.director().setRegistered(true);
        directorRepository.save(req.director());

        if (!isAllowedToSign(req.x500Name(), req.director())) {
            companyService.suspendCompany(req.director().getCompany());
            log.warn("Peppol not activated for email={} director={} signer={} {} serial={}", account.getEmail(), req.director().getName(), getRDNName(req.x500Name(), BCStyle.GIVENNAME), getRDNName(req.x500Name(), BCStyle.SURNAME), req.x509Certificate().getSerialNumber());
            sendManualVerificationEmail(account.getEmail(), req.director().getCompany().getPeppolId(), req.director().getCompany().getName(), req.director().getName(), getRDNName(req.x500Name(), BCStyle.GIVENNAME), getRDNName(req.x500Name(), BCStyle.SURNAME)); //TODO : check, mail does not work !
        }

        log.info("Identity verified for email={} director={} serial={}", account.getEmail(), req.director().getName(), req.x509Certificate().getSerialNumber());
        return account;
    }

//    public Account createUser(UUID adminExternalId, NewUserRequest req) {
//        Account admin = accountService.getAdminByExternalId(adminExternalId);
//        accountService.verifyEmailNotRegistered(req.email());
//        Account account = new Account();
//        account.setType(AccountType.USER);
//        account.setName(req.name());
//        account.setEmail(req.email().toLowerCase());
//        account.setIdentityVerified(false);
//        account.setIdentityVerifiedOn(null);
//        account.setCreatedOn(Instant.now());
//        account.setPasswordHash("TODO");
//        account.setCompany(admin.getCompany());
//        accountService.create(account);
//        ownershipService.link(account, AccountType.USER, admin.getCompany());
//
//        //TODO : send User Activation Mail
//
//        accountService.link(admin, account);
//
//        log.info("New user created name={} email={} company={}", account.getName(), account.getEmail(), account.getCompany().getPeppolId());
//        return account;
//    }

    private String getCN(X509Certificate certificate) {
        X500Principal principal = new X500Principal(certificate.getSubjectX500Principal().getEncoded());
        X500Name x500Name = new X500Name(principal.getName());
        return getRDNName(x500Name, BCStyle.CN);
    }

    private void sendManualVerificationEmail(String email, String peppolId, String companyName, String directorName, String signerGivenName, String signerSurName) {
        log.info("Sending manual verification email to intervention@letspeppol.org for company {} {} for account {}", peppolId, companyName, email);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setTo("intervention@letspeppol.org");
            helper.setFrom("kyc@letspeppol.org", "Let's Peppol");
            helper.setReplyTo("support@letspeppol.org");
            helper.setSubject("Activation on hold for " + peppolId);
            helper.setText(String.format("User %s %s with account %s requested Peppol access for company %s with PeppolId %s and should be represented by %s.",
                    signerGivenName, signerSurName, companyName, peppolId, directorName), false);
            mailSender.send(message);
            log.info("Sent manual verification email to intervention@letspeppol.org for company {} {} for account {}", peppolId, companyName, email);
        } catch (Exception e) {
            log.warn("Failed to send manual verification email error={}", e.getMessage());
        }
    }
}
