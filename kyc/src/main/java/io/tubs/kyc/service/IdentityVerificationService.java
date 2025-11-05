package io.tubs.kyc.service;

import io.tubs.kyc.dto.IdentityVerificationRequest;
import io.tubs.kyc.exception.KycErrorCodes;
import io.tubs.kyc.exception.KycException;
import io.tubs.kyc.model.User;
import io.tubs.kyc.model.UserIdentityVerification;
import io.tubs.kyc.repository.UserIdentityVerificationRepository;
import io.tubs.kyc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;

import static io.tubs.kyc.service.signing.CertificateUtil.getRDNName;

@Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class IdentityVerificationService {

    private final AppService appService;
    private final UserIdentityVerificationRepository civRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final JwtService jwtService;
    private final LetsPeppolProxyService letsPeppolProxyService;
    private final PasswordEncoder passwordEncoder;

    public void verifyNotRegistered(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new KycException(KycErrorCodes.USER_ALREADY_LINKED);
        }
    }

    public User create(IdentityVerificationRequest req) {
        verifyNotRegistered(req.email());

        User user = new User();
        user.setEmail(req.email());
        user.setIdentityVerified(true);
        user.setIdentityVerifiedAt(Instant.now());
        String passwordHash = passwordEncoder.encode(req.password());
        user.setPasswordHash(passwordHash);
        user.setCompany(req.director().getCompany());
        userRepository.save(user);

        UserIdentityVerification civ = new UserIdentityVerification(
                user,
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

        String token = jwtService.generateToken("0208:" + req.director().getCompany().getCompanyNumber().replaceAll("BE", ""), user.getExternalId()); // TODO ?
        letsPeppolProxyService.registerCompany(token, req.director().getCompany().getName());
        appService.register(req);

        log.info("Identity verified for email={} director={} serial={}", user.getEmail(), req.director().getName(), req.x509Certificate().getSerialNumber());
        return user;
    }

    private String getCN(X509Certificate certificate) {
        X500Principal principal = new X500Principal(certificate.getSubjectX500Principal().getEncoded());
        X500Name x500Name = new X500Name(principal.getName());
        return getRDNName(x500Name, BCStyle.CN);
    }
}
