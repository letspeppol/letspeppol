package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.letspeppol.kyc.dto.IdentityVerificationRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountIdentityVerification;
import org.letspeppol.kyc.repository.AccountIdentityVerificationRepository;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;

import static org.letspeppol.kyc.service.signing.CertificateUtil.getRDNName;

@Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class IdentityVerificationService {

    private final AppService appService;
    private final AccountIdentityVerificationRepository civRepository;
    private final AccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final JwtService jwtService;
    private final ProxyService proxyService;
    private final PasswordEncoder passwordEncoder;

    public void verifyNotRegistered(String email) {
        if (accountRepository.existsByEmail(email)) {
            throw new KycException(KycErrorCodes.ACCOUNT_ALREADY_LINKED);
        }
    }

    public Account create(IdentityVerificationRequest req) {
        verifyNotRegistered(req.email());

        Account account = new Account();
        account.setEmail(req.email());
        account.setIdentityVerified(true);
        account.setIdentityVerifiedOn(Instant.now());
        account.setCreatedOn(Instant.now());
        String passwordHash = passwordEncoder.encode(req.password());
        account.setPasswordHash(passwordHash);
        account.setCompany(req.director().getCompany());
        accountRepository.save(account);

        AccountIdentityVerification civ = new AccountIdentityVerification(
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
        civRepository.save(civ);

        String token = jwtService.generateToken(req.director().getCompany().getPeppolId(), account.getExternalId()); // TODO ?
        proxyService.registerCompany(token, req.director().getCompany().getName());
        appService.register(req);

        log.info("Identity verified for email={} director={} serial={}", account.getEmail(), req.director().getName(), req.x509Certificate().getSerialNumber());
        return account;
    }

    private String getCN(X509Certificate certificate) {
        X500Principal principal = new X500Principal(certificate.getSubjectX500Principal().getEncoded());
        X500Name x500Name = new X500Name(principal.getName());
        return getRDNName(x500Name, BCStyle.CN);
    }
}
