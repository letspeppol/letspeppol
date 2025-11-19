package org.letspeppol.kyc.service.signing;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.signatures.IExternalSignatureContainer;

import java.io.InputStream;
import java.security.cert.X509Certificate;

public class FinalizeSignatureContainer  implements IExternalSignatureContainer {
    private PdfDictionary sigDic;
    private X509Certificate[] chain;
    private byte[] cmsSignature;

    public FinalizeSignatureContainer(X509Certificate[] chain, byte[] cmsSignature) {
        this.chain = chain;
        sigDic = new PdfDictionary();
        sigDic.put(PdfName.Filter, PdfName.Adobe_PPKLite);
        sigDic.put(PdfName.SubFilter, PdfName.Adbe_pkcs7_detached);
        this.cmsSignature = cmsSignature;
    }

    @Override
    public byte[] sign(InputStream data) {
        return cmsSignature;
    }

    @Override
    public void modifySigningDictionary(PdfDictionary signDic) {
        signDic.putAll(sigDic);
    }
}
