package org.letspeppol.kyc.service.signing;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that {@link SignatureUtil#verifyWebEidSignature} correctly accepts genuine Web-eID-style
 * RSA and ECC signatures over a pre-computed SHA-256 digest and rejects tampered/mismatched inputs
 * — without needing a physical eID card.
 */
class SignatureUtilTest {

    private static final byte[] SHA256_DIGESTINFO_PREFIX = {
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
            0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    };

    private static byte[] webEidStyleSign(PrivateKey key, byte[] digest) throws Exception {
        Signature s = Signature.getInstance(webEidSigningAlgorithm(key));
        s.initSign(key);
        s.update(webEidSignedBytes(key, digest));
        return s.sign();
    }

    private static String webEidSigningAlgorithm(PrivateKey key) {
        return switch (key.getAlgorithm()) {
            case "RSA" -> "NONEwithRSA";
            case "EC" -> "NONEwithECDSAinP1363Format";
            default -> throw new IllegalArgumentException("Unsupported test key algorithm: " + key.getAlgorithm());
        };
    }

    private static byte[] webEidSignedBytes(PrivateKey key, byte[] digest) {
        if ("RSA".equals(key.getAlgorithm())) {
            byte[] di = new byte[SHA256_DIGESTINFO_PREFIX.length + digest.length];
            System.arraycopy(SHA256_DIGESTINFO_PREFIX, 0, di, 0, SHA256_DIGESTINFO_PREFIX.length);
            System.arraycopy(digest, 0, di, SHA256_DIGESTINFO_PREFIX.length, digest.length);
            return di;
        }
        return digest;
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static KeyPair ec() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp384r1"));
        return g.generateKeyPair();
    }

    private static X509Certificate selfSigned(KeyPair kp) throws Exception {
        X500Principal dn = new X500Principal("CN=Test eID, GIVENNAME=Test, SURNAME=User");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, BigInteger.ONE,
                new Date(System.currentTimeMillis() - 1000L),
                new Date(System.currentTimeMillis() + 86_400_000L),
                dn, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder(certificateSigningAlgorithm(kp.getPrivate())).build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static String certificateSigningAlgorithm(PrivateKey key) {
        return switch (key.getAlgorithm()) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA384withECDSA";
            default -> throw new IllegalArgumentException("Unsupported test key algorithm: " + key.getAlgorithm());
        };
    }

    private static byte[] randomDigest() {
        byte[] d = new byte[32];
        new SecureRandom().nextBytes(d);
        return d;
    }

    @Test
    void validRsaSignatureVerifies() throws Exception {
        KeyPair kp = rsa();
        X509Certificate cert = selfSigned(kp);
        byte[] digest = randomDigest();
        byte[] sig = webEidStyleSign(kp.getPrivate(), digest);
        assertTrue(SignatureUtil.verifyWebEidSignature(cert, digest, sig));
    }

    @Test
    void validEcSignatureVerifies() throws Exception {
        KeyPair kp = ec();
        X509Certificate cert = selfSigned(kp);
        byte[] digest = randomDigest();
        byte[] sig = webEidStyleSign(kp.getPrivate(), digest);
        assertTrue(SignatureUtil.verifyWebEidSignature(cert, digest, sig));
    }

    @Test
    void tamperedEcSignatureIsRejected() throws Exception {
        KeyPair kp = ec();
        X509Certificate cert = selfSigned(kp);
        byte[] digest = randomDigest();
        byte[] sig = webEidStyleSign(kp.getPrivate(), digest);
        sig[10] ^= 0x01;
        assertFalse(SignatureUtil.verifyWebEidSignature(cert, digest, sig));
    }

    @Test
    void wrongEcDigestIsRejected() throws Exception {
        KeyPair kp = ec();
        X509Certificate cert = selfSigned(kp);
        byte[] digest = randomDigest();
        byte[] sig = webEidStyleSign(kp.getPrivate(), digest);
        assertFalse(SignatureUtil.verifyWebEidSignature(cert, randomDigest(), sig));
    }

    @Test
    void signatureFromADifferentRsaKeyIsRejected() throws Exception {
        KeyPair signerKp = rsa();
        KeyPair attackerCertKp = rsa();
        X509Certificate attackerCert = selfSigned(attackerCertKp); // cert public key != signer key
        byte[] digest = randomDigest();
        byte[] sig = webEidStyleSign(signerKp.getPrivate(), digest);
        assertFalse(SignatureUtil.verifyWebEidSignature(attackerCert, digest, sig));
    }

    @Test
    void nonSha256DigestLengthIsRejected() throws Exception {
        KeyPair kp = ec();
        X509Certificate cert = selfSigned(kp);
        assertFalse(SignatureUtil.verifyWebEidSignature(cert, new byte[31], new byte[96]));
    }

    @Test
    void ecSignatureWrapperEmbedsCmsDerEcdsaSignature() throws Exception {
        KeyPair kp = ec();
        X509Certificate cert = selfSigned(kp);
        byte[] digest = randomDigest();
        byte[] rawP1363Signature = webEidStyleSign(kp.getPrivate(), digest);

        byte[] cmsBytes = EmbeddableSignatureUtil.wrapWebEidSignature(new X509Certificate[]{cert}, rawP1363Signature, digest);

        CMSSignedData cms = new CMSSignedData(cmsBytes);
        SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
        assertEquals("1.2.840.10045.4.3.2", signer.getEncryptionAlgOID());
        assertEquals(2, ASN1Sequence.getInstance(signer.getSignature()).size());
    }
}
