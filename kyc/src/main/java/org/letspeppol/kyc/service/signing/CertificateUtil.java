package org.letspeppol.kyc.service.signing;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class CertificateUtil {

    public static X509Certificate[] getCertificateChain(String userCertificateBase64) throws Exception {
        return new X509Certificate[] {
                CertificateUtil.parseCertificate(userCertificateBase64),
        };
    }

    public static X509Certificate loadCertificateFromClasspath(String path) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        try (InputStream is = CertificateUtil.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Certificate not found at path: " + path);
            }
            return (X509Certificate) cf.generateCertificate(is);
        }
    }

    public static X509Certificate parseCertificate(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(der));
        } catch (CertificateException e) {
            throw new KycException(KycErrorCodes.INVALID_CERTIFICATE);
        }
    }

    public static String getRDNName(X500Name x500Name, ASN1ObjectIdentifier identifier) {
        RDN[] rdns = x500Name.getRDNs(identifier);
        for (RDN rdn : rdns) {
            return IETFUtils.valueToString(rdn.getFirst().getValue());
        }
        return null;
    }
}
