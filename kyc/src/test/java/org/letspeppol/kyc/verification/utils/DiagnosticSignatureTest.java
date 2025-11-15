package org.letspeppol.kyc.verification.utils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

@Disabled
public class DiagnosticSignatureTest {

    // paste your strings here
    String certificate = null;
    String hashToSign = null;
    String signatureB64 = null;

    // SHA-256 DigestInfo prefix (DER) for checking DigestInfo(SHA-256, digest)
    // hex: 3031300d060960864801650304020105000420
    public static final byte[] SHA256_DIGESTINFO_PREFIX = hex("3031300d060960864801650304020105000420");

    @Test
    public void diagnose() throws Exception {
        byte[] certBytes = Base64.getDecoder().decode(certificate);
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certBytes));

        byte[] digest = Base64.getDecoder().decode(hashToSign);
        byte[] signature = Base64.getDecoder().decode(signatureB64);

        System.out.println("pubKey algorithm: " + cert.getPublicKey().getAlgorithm());
        RSAPublicKey pub = (RSAPublicKey) cert.getPublicKey();
        System.out.println("modulus bits: " + pub.getModulus().bitLength());
        System.out.println("signature length (bytes): " + signature.length);

        // RSA public op: m = s^e mod n  (gives the PKCS#1 v1.5 encoded block)
        BigInteger s = new BigInteger(1, signature);
        BigInteger m = s.modPow(pub.getPublicExponent(), pub.getModulus());
        byte[] em = toFixedLength(m.toByteArray(), pub.getModulus().bitLength() / 8);

        System.out.println("EM (first 96 hex chars): " + toHex(em).substring(0, Math.min(96, toHex(em).length())));
        // parse PKCS#1 v1.5 padding
        if (em.length < 11) {
            System.out.println("EM too short");
            return;
        }
        if (em[0] != 0x00 || em[1] != 0x01) {
            System.out.println("EM does not start with 0x00 0x01 - unexpected");
        } else {
            int idx = 2;
            while (idx < em.length && em[idx] == (byte) 0xFF) idx++;
            if (idx >= em.length || em[idx] != 0x00) {
                System.out.println("padding end not found as expected, byte at idx=" + idx + " = " + em[idx]);
            } else {
                idx++; // start of payload
                byte[] payload = Arrays.copyOfRange(em, idx, em.length);
                System.out.println("payload length: " + payload.length);
                System.out.println("payload hex (prefix): " + toHex(payload).substring(0, Math.min(120, toHex(payload).length())));

                // case A: DigestInfo(SHA-256) + digest
                if (payload.length == SHA256_DIGESTINFO_PREFIX.length + digest.length &&
                        Arrays.equals(Arrays.copyOf(payload, SHA256_DIGESTINFO_PREFIX.length), SHA256_DIGESTINFO_PREFIX) &&
                        Arrays.equals(Arrays.copyOfRange(payload, SHA256_DIGESTINFO_PREFIX.length, payload.length), digest)) {
                    System.out.println("=> SIGNATURE IS RSA(DigestInfo(SHA-256, digest)). (Common: signer performed DigestInfo+RSA).");
                }
                // case B: raw digest (rare)
                else if (payload.length == digest.length && Arrays.equals(payload, digest)) {
                    System.out.println("=> SIGNATURE IS RSA(raw-digest). (Signer signed raw digest bytes).");
                } else {
                    System.out.println("=> PAYLOAD does not match DigestInfo nor raw-digest. May be authenticated attributes or different hashing.");
                    System.out.println("payload (base64): " + Base64.getEncoder().encodeToString(payload));
                }

                // Try Java Signature verifications:
                Signature sNone = Signature.getInstance("NONEwithRSA");
                sNone.initVerify(cert.getPublicKey());
                sNone.update(digest);
                System.out.println("NONEwithRSA(digest) => " + sNone.verify(signature));

                // try NONEwithRSA on DigestInfo if we have DigestInfo prefix
                byte[] digestInfo = concat(SHA256_DIGESTINFO_PREFIX, digest);
                Signature sNone2 = Signature.getInstance("NONEwithRSA");
                sNone2.initVerify(cert.getPublicKey());
                sNone2.update(digestInfo);
                System.out.println("NONEwithRSA(DigestInfo) => " + sNone2.verify(signature));
            }
        }
    }

    // helpers
    private static byte[] toFixedLength(byte[] in, int length) {
        if (in.length == length) return in;
        if (in.length == length + 1 && in[0] == 0x00) {
            return Arrays.copyOfRange(in, 1, in.length);
        }
        if (in.length < length) {
            byte[] out = new byte[length];
            System.arraycopy(in, 0, out, length - in.length, in.length);
            return out;
        }
        return in;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(s.substring(i, i + 2), 16);
        }
        return out;
    }
}
