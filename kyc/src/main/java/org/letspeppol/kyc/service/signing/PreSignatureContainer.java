package org.letspeppol.kyc.service.signing;

import com.itextpdf.kernel.crypto.DigestAlgorithms;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.IExternalSignatureContainer;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

public class PreSignatureContainer implements IExternalSignatureContainer {
    private PdfDictionary sigDic;
    private byte hash[];
//    private X509Certificate[] chain;

    public PreSignatureContainer() {//(X509Certificate[] chain) {
//        this.chain = chain;
        sigDic = new PdfDictionary();
        sigDic.put(PdfName.Filter, PdfName.Adobe_PPKLite);
        sigDic.put(PdfName.SubFilter, PdfName.Adbe_pkcs7_detached);
    }

    @Override
    public byte[] sign(InputStream data) throws GeneralSecurityException {
        String hashAlgorithm = "SHA256";
        BouncyCastleDigest digest = new BouncyCastleDigest();

        try {
            this.hash = DigestAlgorithms.digest(data, digest.getMessageDigest(hashAlgorithm));
        } catch (IOException e) {
            throw new GeneralSecurityException("PreSignatureContainer signing exception", e);
        }

        return new byte[0];
    }

    @Override
    public void modifySigningDictionary(PdfDictionary signDic) {
        signDic.putAll(sigDic);
//            PdfArray certArray = new PdfArray();
//            for (X509Certificate c : chain) {
//                try {
//                    certArray.add(new PdfString(c.getEncoded()));
//                } catch (CertificateEncodingException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//            signDic.put(PdfName.Cert, certArray);
    }

    public byte[] getHash() {
        return hash;
    }
}
