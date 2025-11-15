package org.letspeppol.kyc.verification.utils;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.signatures.IExternalSignatureContainer;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.SignerProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import static org.letspeppol.kyc.service.SigningService.SIGNATURE_CONTENT;
import static org.letspeppol.kyc.service.SigningService.getSignerProperties;

@Disabled
public class PdfSignatureAppearanceTest {

    @Test
    public void generatePdf() throws Exception {
        File preparedPdf = Path.of(System.getProperty("java.io.tmpdir"), "contract_en_prepared.pdf").toFile();
        try (InputStream resource = getClass().getResourceAsStream("/docs/contract_en.pdf");
             PdfReader pdfReader = new PdfReader(resource);
             OutputStream outputStream = new FileOutputStream(preparedPdf)) {

            PdfSigner signer = new PdfSigner(pdfReader, outputStream, new StampingProperties().useAppendMode());

            String content = SIGNATURE_CONTENT.formatted(
                    "Wout Schattebout",
                    "860807344109",
                    "09/09/2025 15:21:11",
                    "01234556789",
                    "Wout Peter Schattebout"
            );

            SignerProperties signerProperties = getSignerProperties(content);
            signer.setSignerProperties(signerProperties);
            signer.signExternalContainer(new TestSignatureContainer(), 1600);
        }
    }

    public static class TestSignatureContainer implements IExternalSignatureContainer {
        private PdfDictionary sigDic;

        public TestSignatureContainer() {
            sigDic = new PdfDictionary();
            sigDic.put(PdfName.Filter, PdfName.Adobe_PPKLite);
            sigDic.put(PdfName.SubFilter, PdfName.Adbe_pkcs7_detached);
        }

        @Override
        public byte[] sign(InputStream data) {
            return new byte[0];
        }

        @Override
        public void modifySigningDictionary(PdfDictionary signDic) {
            signDic.putAll(sigDic);
        }
    }

}
