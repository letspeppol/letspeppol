package org.letspeppol.kyc.service.signing;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class SignatureUtil {

    public static boolean verifySignature(String algorithm, X509Certificate cert, String payload, String sigBase64) {
        try {
            Signature sig = Signature.getInstance(algorithm);
            sig.initVerify(cert.getPublicKey());
            sig.update(payload.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(sigBase64));
        } catch (Exception e) {
            throw new RuntimeException("Signature verification failure: " + e.getMessage());
        }
    }
}
