package org.letspeppol.kyc.service.signing;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

public class EmbeddableSignatureUtil {

    /**
     * Wrap a Web-eID signature into a detached PKCS#7 signature that iText can embed in a PDF.
     * RSA Web-eID signatures are PKCS#1 bytes. ECC Web-eID signatures are IEEE P1363 r||s bytes,
     * but CMS stores ECDSA signatures as ASN.1 DER, so they are converted before embedding.
     *
     * @param certificates   Certificate chain (first = signer)
     * @param extSignature   Web-eID signature bytes
     * @param pdfDigest      SHA-256 digest of the PDF byte ranges (hashToSign)
     * @return PKCS#7 DER-encoded bytes
     */
    public static byte[] wrapWebEidSignature(X509Certificate[] certificates, byte[] extSignature, byte[] pdfDigest) throws Exception {
        // Ensure BouncyCastle provider is registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // 1. Build a PdfPKCS7-like CMS signer (external signature)
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        String signatureAlgorithm = cmsSignatureAlgorithm(certificates[0].getPublicKey());
        byte[] signatureValue = cmsSignatureValue(certificates[0].getPublicKey(), extSignature);

        // 2. Provide a ContentSigner that just returns the Web-eID signature
        ContentSigner cs = new ContentSigner() {
            @Override
            public byte[] getSignature() {
                return signatureValue;
            }

            @Override
            public AlgorithmIdentifier getAlgorithmIdentifier() {
                return new DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithm);
            }

            @Override
            public java.io.OutputStream getOutputStream() {
                // Web-eID already signed, so we don't need to write anything here
                return new java.io.ByteArrayOutputStream();
            }
        };

        // 3. Add the signer info (end-entity cert) with direct signature (no signed attributes)
        JcaSignerInfoGeneratorBuilder sigInfoBuilder = new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build()
        );
        // Direct signature means the signature is over the content, not over authenticated attributes
        sigInfoBuilder.setDirectSignature(true);
        gen.addSignerInfoGenerator(sigInfoBuilder.build(cs, certificates[0]));

        // 4. Add all certificates to the CMS structure (flatten list)
        gen.addCertificates(new JcaCertStore(Arrays.asList(certificates)));

        // 5. Create detached CMS (content = pdfDigest)
        CMSProcessableByteArray content = new CMSProcessableByteArray(pdfDigest);
        CMSSignedData signedData = gen.generate(content, false); // false = detached

        return signedData.getEncoded();
    }

    private static String cmsSignatureAlgorithm(PublicKey publicKey) {
        return switch (publicKey) {
            case RSAPublicKey _ -> "SHA256withRSA";
            case ECPublicKey _ -> "SHA256withECDSA";
            default -> throw new IllegalArgumentException(
                    "Unsupported Web-eID public key algorithm: " + publicKey.getAlgorithm());
        };
    }

    private static byte[] cmsSignatureValue(PublicKey publicKey, byte[] extSignature) throws Exception {
        return switch (publicKey) {
            // Web-eID returns ECDSA signatures in P1363 form; CMS expects DER.
            case ECPublicKey _ -> ecdsaP1363ToDer(extSignature);
            default -> extSignature;
        };
    }

    private static byte[] ecdsaP1363ToDer(byte[] signature) throws Exception {
        if ((signature.length & 1) != 0) {
            throw new IllegalArgumentException("Invalid ECDSA P1363 signature length: " + signature.length);
        }
        int coordinateLength = signature.length / 2;
        ASN1EncodableVector sequence = new ASN1EncodableVector(2);
        sequence.add(new ASN1Integer(new BigInteger(1, Arrays.copyOfRange(signature, 0, coordinateLength))));
        sequence.add(new ASN1Integer(new BigInteger(1, Arrays.copyOfRange(signature, coordinateLength, signature.length))));
        return new DERSequence(sequence).getEncoded();
    }

}
