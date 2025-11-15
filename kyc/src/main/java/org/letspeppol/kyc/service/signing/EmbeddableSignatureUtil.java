package org.letspeppol.kyc.service.signing;

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

import java.security.Security;
import java.security.cert.X509Certificate;

public class EmbeddableSignatureUtil {

    /**
     * Wrap a Web-eID signature (RSA PKCS#1 over DigestInfo) into a detached PKCS#7 signature
     * that iText can embed in a PDF.
     *
     * @param certificates   Certificate chain (first = signer)
     * @param extSignature   Web-eID signature bytes (RSA(DigestInfo(SHA-256, digest)))
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

        // 2. Provide a ContentSigner that just returns the Web-eID signature
        ContentSigner cs = new ContentSigner() {
            @Override
            public byte[] getSignature() {
                return extSignature; // Web-eID PKCS#1 signature over DigestInfo(SHA-256, pdfDigest)
            }

            @Override
            public AlgorithmIdentifier getAlgorithmIdentifier() {
                return new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
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
        // Direct signature means the RSA signature is over the content, not over authenticated attributes
        sigInfoBuilder.setDirectSignature(true);
        gen.addSignerInfoGenerator(sigInfoBuilder.build(cs, certificates[0]));

        // 4. Add all certificates to the CMS structure (flatten list)
        gen.addCertificates(new JcaCertStore(java.util.Arrays.asList(certificates)));

        // 5. Create detached CMS (content = pdfDigest)
        CMSProcessableByteArray content = new CMSProcessableByteArray(pdfDigest);
        CMSSignedData signedData = gen.generate(content, false); // false = detached

        return signedData.getEncoded();
    }

}
