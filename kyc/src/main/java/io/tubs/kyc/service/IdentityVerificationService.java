package io.tubs.kyc.service;

import io.tubs.kyc.dto.IdentityVerificationRequest;
import io.tubs.kyc.exception.KycException;
import io.tubs.kyc.model.Customer;
import io.tubs.kyc.model.CustomerIdentityVerification;
import io.tubs.kyc.repository.CustomerIdentityVerificationRepository;
import io.tubs.kyc.repository.CustomerRepository;
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
    private final CustomerIdentityVerificationRepository civRepository;
    private final CustomerRepository customerRepository;
    private final EncryptionService encryptionService;
    private final JwtService jwtService;
    private final LetsPeppolProxyService letsPeppolProxyService;
    private final PasswordEncoder passwordEncoder;

    public void verifyNotRegistered(String email) {
        if (customerRepository.existsByEmail(email)) {
            throw new KycException("Customer already linked to company");
        }
    }

    public void create(IdentityVerificationRequest req) {
        verifyNotRegistered(req.email());

        Customer customer = new  Customer();
        customer.setEmail(req.email());
        customer.setIdentityVerified(true);
        customer.setIdentityVerifiedAt(Instant.now());
        String passwordHash = passwordEncoder.encode(req.password());
        customer.setPasswordHash(passwordHash);
        customer.setCompany(req.director().getCompany());
        customerRepository.save(customer);

        CustomerIdentityVerification civ = new CustomerIdentityVerification(
                customer,
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

        String token = jwtService.generateToken("0208:" + req.director().getCompany().getCompanyNumber().replaceAll("BE", "")); // TODO ?
        letsPeppolProxyService.registerCompany(token);
        appService.register(req);

        log.info("Identity verified for email={} director={} serial={}", customer.getEmail(), req.director().getName(), req.x509Certificate().getSerialNumber());
    }

    private String getCN(X509Certificate certificate) {
        X500Principal principal = new X500Principal(certificate.getSubjectX500Principal().getEncoded());
        X500Name x500Name = new X500Name(principal.getName());
        return getRDNName(x500Name, BCStyle.CN);
    }
}
