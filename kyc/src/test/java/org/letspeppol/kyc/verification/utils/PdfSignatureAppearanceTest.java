package org.letspeppol.kyc.verification.utils;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.signatures.IExternalSignatureContainer;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.SignerProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.Path;

import static org.letspeppol.kyc.service.SigningService.SIGNATURE_CONTENT;
import static org.letspeppol.kyc.service.SigningService.getSignerProperties;

//@Disabled
public class PdfSignatureAppearanceTest {

    @Test
    public void fillPdf() throws Exception {
        File filledPdf = Path.of("contract_en_filled.pdf").toFile(); //System.getProperty("java.io.tmpdir"),
        try (InputStream resource = getClass().getResourceAsStream("/docs/LetsPeppol_contract_template.pdf")) {
            if (resource == null) throw new FileNotFoundException("Classpath resource not found: /docs/LetsPeppol_contract_template.pdf");
            byte[] bytes = resource.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
                if (form == null) throw new IllegalStateException("Template has no AcroForm fields");

                form.getField("company").setValue("Banana Corp");
                form.getField("identification").setValue(
                        "Banana Corp, a private limited liability company incorporated according to Belgian law, " +
                        "with registered office at Kerkstraat 42, 1234 Brullelijk, registered at the " +
                        "Belgian Crossroads Bank for Enterprises under number: 1234.567.890, "+
                        "hereby duly represented by her director, Wout Schattebout;");
                form.getField("representative").setValue("Wout Schattebout");
                form.getField("title").setValue("Director");

                form.refreshAppearances();   // PDFBox 3: regenerate form appearances
                form.flatten(); //final, non-form PDF

                try (var baos = new FileOutputStream(filledPdf)) {
                    doc.save(baos);
                }
            }
        }
    }

    @Test
    public void generatePdf() throws Exception {
        File preparedPdf = Path.of("contract_en_prepared.pdf").toFile(); //System.getProperty("java.io.tmpdir"),
        try (InputStream resource = getClass().getResourceAsStream("/docs/LetsPeppol_contract_template.pdf");
             PdfReader pdfReader = new PdfReader(resource);
             OutputStream outputStream = new FileOutputStream(preparedPdf)) {

            PdfSigner signer = new PdfSigner(pdfReader, outputStream, new StampingProperties().useAppendMode());

            String content = SIGNATURE_CONTENT.formatted(
                    "Wout Schattebout Meteenenormelangenaamdielimietentest",
                    "860807344109",
                    "09/09/2025 15:21:11",
                    "Big Bad Bunny Boss Bringing Banana Boat Business Bv",
                    "01234556789",
                    "Wout Peter Schattebout Meteenenormelangenaamdielimietentest"
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
