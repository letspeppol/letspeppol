package org.letspeppol.kyc.service.signing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
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

    // ASN.1 DigestInfo prefix for SHA-256 (RFC 8017, EMSA-PKCS1-v1_5).
    private static final byte[] SHA256_DIGESTINFO_PREFIX = {
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
            0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    };

    private static final String RAW_RSA = "NONEwithRSA";
    private static final String RAW_ECDSA_P1363 = "NONEwithECDSAinP1363Format";

    /**
     * Cryptographically verifies a Web-eID signature made over a pre-computed SHA-256 digest.
     * RSA signatures are RSASSA-PKCS1-v1_5 over DigestInfo(SHA-256, pdfDigest). ECC signatures
     * are raw IEEE P1363 ECDSA signatures over {@code pdfDigest}, which is what Web-eID returns
     * for certificates whose public key is an EC key. Returns {@code false} on any error or
     * invalid input (never throws).
     */
    public static boolean verifyWebEidSignature(X509Certificate cert, byte[] pdfDigest, byte[] signature) {
        try {
            if (cert == null || pdfDigest == null || signature == null || pdfDigest.length != 32) {
                return false;
            }
            PublicKey publicKey = cert.getPublicKey();
            Signature sig = webEidSignature(publicKey);
            sig.initVerify(publicKey);
            sig.update(webEidSignedBytes(publicKey, pdfDigest));
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static Signature webEidSignature(PublicKey publicKey) throws GeneralSecurityException {
        if (publicKey instanceof RSAPublicKey) {
            return Signature.getInstance(RAW_RSA);
        }
        if (publicKey instanceof ECPublicKey) {
            return Signature.getInstance(RAW_ECDSA_P1363);
        }
        throw new InvalidKeyException("Unsupported Web-eID public key algorithm: " + publicKey.getAlgorithm());
    }

    private static byte[] webEidSignedBytes(PublicKey publicKey, byte[] pdfDigest) {
        if (publicKey instanceof RSAPublicKey) {
            byte[] digestInfo = new byte[SHA256_DIGESTINFO_PREFIX.length + pdfDigest.length];
            System.arraycopy(SHA256_DIGESTINFO_PREFIX, 0, digestInfo, 0, SHA256_DIGESTINFO_PREFIX.length);
            System.arraycopy(pdfDigest, 0, digestInfo, SHA256_DIGESTINFO_PREFIX.length, pdfDigest.length);
            return digestInfo;
        }
        return pdfDigest;
    }
}
