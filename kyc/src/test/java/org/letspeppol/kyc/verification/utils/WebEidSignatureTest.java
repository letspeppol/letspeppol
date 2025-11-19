package org.letspeppol.kyc.verification.utils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.letspeppol.kyc.service.signing.CertificateUtil.parseCertificate;
import static org.letspeppol.kyc.verification.utils.DiagnosticSignatureTest.SHA256_DIGESTINFO_PREFIX;

@Disabled
public class WebEidSignatureTest {

    /*
     * Test to check whether signature read with e.g. Web-eID are valid
     */
    @Test
    public void isValid() throws Exception {
        String certificate = null;
        String hashToSign = null;
        String signature = null;

        byte[] data = Base64.getDecoder().decode(hashToSign);
        byte[] signatureBytes = Base64.getDecoder().decode(signature);

        X509Certificate x509Certificate = parseCertificate(certificate);
        boolean verified = verifySignature(x509Certificate, data, signatureBytes);
        assertTrue(verified);
    }

    public boolean verifySignature(X509Certificate signerCert, byte[] hashToSign, byte[] signatureBytes) throws Exception {
        Signature sig = Signature.getInstance("NONEwithRSA");
        sig.initVerify(signerCert.getPublicKey());

        byte[] digestInfo = concat(SHA256_DIGESTINFO_PREFIX, hashToSign);
        sig.update(digestInfo);

        return sig.verify(signatureBytes);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

}
