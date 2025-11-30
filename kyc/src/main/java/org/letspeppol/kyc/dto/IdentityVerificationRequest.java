package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.bouncycastle.asn1.x500.X500Name;
import org.letspeppol.kyc.model.kbo.Director;
import java.security.cert.X509Certificate;

public record IdentityVerificationRequest(
        @NotNull String email,
        @NotNull Director director,
        @NotBlank @Size(max = 64) String password,
        @NotBlank @Size(max = 64) String algorithm,
        @NotNull String hashToSign,
        @NotNull String signature,
        @NotNull String certificate,
        @NotNull X509Certificate x509Certificate,
        X500Name x500Name
) {}

