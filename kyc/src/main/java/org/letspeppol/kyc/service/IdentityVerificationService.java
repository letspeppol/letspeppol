package org.letspeppol.kyc.service;

import io.micrometer.core.instrument.Counter;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.letspeppol.kyc.dto.IdentityVerificationRequest;
import org.letspeppol.kyc.dto.IdentityVerificationResponse;
import org.letspeppol.kyc.dto.NewUserRequest;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountIdentityVerification;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.repository.AccountIdentityVerificationRepository;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;
import static org.letspeppol.kyc.service.SigningService.isAllowedToSign;
import static org.letspeppol.kyc.service.signing.CertificateUtil.getRDNName;

@Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class IdentityVerificationService {

    private final AccountIdentityVerificationRepository accountIdentityVerificationRepository;
    private final AccountService accountService;
    private final DirectorRepository directorRepository;
    private final JavaMailSender mailSender;
    private final EncryptionService encryptionService;
    private final CompanyService companyService;
    private final PasswordEncoder passwordEncoder;
    private final Counter companyRegistrationCounterSuccess;
    private final Counter companyRegistrationCounterFailure;

    public IdentityVerificationResponse createAdmin(IdentityVerificationRequest req) {
        accountService.verifyNotRegistered(req.email());

        Account account = new Account();
        account.setType(AccountType.ADMIN);
        account.setName(req.director().getName());
        account.setEmail(req.email().toLowerCase());
        account.setIdentityVerified(true);
        account.setIdentityVerifiedOn(Instant.now());
        account.setCreatedOn(Instant.now());
        String passwordHash = passwordEncoder.encode(req.password());
        account.setPasswordHash(passwordHash);
        account.setCompany(req.director().getCompany());
        accountService.create(account);

        AccountIdentityVerification accountIdentityVerification = new AccountIdentityVerification(
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
        accountIdentityVerificationRepository.save(accountIdentityVerification);

        req.director().setRegistered(true);
        directorRepository.save(req.director());

        RegistrationResponse registrationResponse = null;
        if (isAllowedToSign(req.x500Name(), req.director())) {
            registrationResponse = companyService.registerCompany(req.director().getCompany());
            if (registrationResponse.peppolActive() && registrationResponse.errorCode() == null) {
                companyRegistrationCounterSuccess.increment();
            } else if(!registrationResponse.peppolActive()) {
                companyRegistrationCounterFailure.increment();
            }
        } else {
            companyService.suspendCompany(req.director().getCompany());
            log.warn("Peppol not activated for email={} director={} signer={} {} serial={}", account.getEmail(), req.director().getName(), getRDNName(req.x500Name(), BCStyle.GIVENNAME), getRDNName(req.x500Name(), BCStyle.SURNAME), req.x509Certificate().getSerialNumber());
            sendManualVerificationEmail(account.getEmail(), req.director().getCompany().getPeppolId(), req.director().getCompany().getName(), req.director().getName(), getRDNName(req.x500Name(), BCStyle.GIVENNAME), getRDNName(req.x500Name(), BCStyle.SURNAME)); //TODO : check, mail does not work !
        }

        log.info("Identity verified for email={} director={} serial={}", account.getEmail(), req.director().getName(), req.x509Certificate().getSerialNumber());
        return new IdentityVerificationResponse(account, registrationResponse);
    }

    public Account createUser(Account admin, NewUserRequest req) {
        accountService.verifyNotRegistered(req.email());
        Account account = new Account();
        account.setType(AccountType.USER);
        account.setName(req.name());
        account.setEmail(req.email().toLowerCase());
        account.setIdentityVerified(false);
        account.setIdentityVerifiedOn(null);
        account.setCreatedOn(Instant.now());
        account.setPasswordHash("TODO");
        account.setCompany(admin.getCompany());
        accountService.create(account);

        //TODO : send User Activation Mail

        accountService.link(admin, account);

        log.info("New user created name={} email={} company={}", account.getName(), account.getEmail(), account.getCompany().getPeppolId());
        return account;
        //return new IdentityVerificationResponse(account, new RegistrationResponse(account.getCompany().isPeppolActive(), null, null));
    }

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
