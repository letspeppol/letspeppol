package org.letspeppol.kyc.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.dto.IdentityVerificationRequest;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.DirectorIdentityVerification;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.AccountIdentityVerificationRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.springframework.mail.javamail.JavaMailSender;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityVerificationServiceTest {

    private final AccountIdentityVerificationRepository verificationRepository = mock(AccountIdentityVerificationRepository.class);
    private final DirectorRepository directorRepository = mock(DirectorRepository.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);
    private final IdentityVerificationService service = new IdentityVerificationService(
            verificationRepository, directorRepository, mock(JavaMailSender.class), encryptionService);

    @Test
    void signerMatchingTheDirectorRegistersTheDirector() {
        Director director = director();

        boolean signerIsDirector = service.recordDirectorSignature(new Account(), request(director, "Jan", "Peeters"));

        assertThat(signerIsDirector).isTrue();
        assertThat(director.isRegistered()).isTrue();
        verify(directorRepository).save(director);
    }

    @Test
    void signerNotMatchingTheDirectorIsKeptForManualReviewWithoutTouchingTheCompany() {
        Director director = director();

        boolean signerIsDirector = service.recordDirectorSignature(new Account(), request(director, "Piet", "Janssens"));

        assertThat(signerIsDirector).isFalse();
        assertThat(director.isRegistered()).isFalse();
        assertThat(director.getCompany().isSuspended()).isFalse();
        verify(verificationRepository).save(any(DirectorIdentityVerification.class));
        verify(directorRepository, never()).save(any());
    }

    private Director director() {
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted");
        return new Director("Jan Peeters", new Company("0208:0123456789", "0123456789", "BE0123456789", "Peeters BV"));
    }

    private static IdentityVerificationRequest request(Director director, String givenName, String surname) {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=" + givenName + " " + surname));
        when(certificate.getSerialNumber()).thenReturn(BigInteger.ONE);
        X500Name x500Name = new X500Name("CN=" + givenName + " " + surname + ", GIVENNAME=" + givenName + ", SURNAME=" + surname);
        return new IdentityVerificationRequest(director, "ES256", "hash", "signature", "certificate", certificate, x500Name);
    }
}
