package org.letspeppol.kyc.verification.utils;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.SignatureUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class PdfSignatureValidationTest {

    /*
     * Test to check whether PDF signatures are valid
     */
    @Test
    public void verifySignatures() throws GeneralSecurityException, IOException {
        verifyPdfDocument("/tmp/contract_en_1234567890.pdf");
    }

    public void verifyPdfDocument(String path) throws IOException, GeneralSecurityException {
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(path));
        SignatureUtil signUtil = new SignatureUtil(pdfDoc);
        List<String> names = signUtil.getSignatureNames();

        System.out.println(path);
        for (String name : names) {
            System.out.println("===== " + name + " =====");
            verify(signUtil, name);
        }

        pdfDoc.close();
    }

    public void verify(SignatureUtil signUtil, String name) throws GeneralSecurityException {
        PdfPKCS7 pkcs7 = signUtil.readSignatureData(name);

        System.out.println("Signature covers whole document: " + signUtil.signatureCoversWholeDocument(name));
        System.out.println("Document revision: " + signUtil.getRevision(name) + " of " + signUtil.getTotalRevisions());
        assertTrue(pkcs7.verifySignatureIntegrityAndAuthenticity());
    }
}
