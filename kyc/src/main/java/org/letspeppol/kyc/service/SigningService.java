package org.letspeppol.kyc.service;

import com.itextpdf.forms.form.element.SignatureFieldAppearance;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.properties.*;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.SignerProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.letspeppol.kyc.service.signing.CertificateUtil;
import org.letspeppol.kyc.service.signing.EmbeddableSignatureUtil;
import org.letspeppol.kyc.service.signing.FinalizeSignatureContainer;
import org.letspeppol.kyc.service.signing.PreSignatureContainer;
import org.letspeppol.kyc.util.NameMatchUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import static org.letspeppol.kyc.service.signing.CertificateUtil.getRDNName;

@Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class SigningService {

    private final ActivationService activationService;

    public static final String LETS_PEPPOL_CONTRACT_TEMPLATE = "/docs/LetsPeppol_contract_template.pdf";
    public static final String IDENTIFICATION_CONTENT = "%s, a legal entity according to Belgian law, " +
            "with registered office at %s, registered at the Belgian Crossroads Bank for Enterprises under number: %s, " +
            "hereby duly represented by her %s, %s;";
    public static final String SIGNATURE_CONTENT = "Digitally signed by %s [%s]\nDate: %s\nCompany: %s [%s]\nDirector: %s";
    public static final String SIGNING_FONTS = "fonts/DejaVuSans.ttf";
    public static final String SIGNING_BACKGROUND_LOGO = "images/logo_background.png";
    public static final String SIGNING_FORMFIELD = "Signature1";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    private final IdentityVerificationService identityVerificationService;
    private final DirectorRepository directorRepository;

    @Value("${kyc.data.dir:#{null}}")
    private String dataDirectory;

    private String workingDirectory;
    private String contractDirectory;

    @PostConstruct
    public void init() throws IOException {
        workingDirectory = initDirectory( "/temp");
        contractDirectory = initDirectory( "/contracts");
    }

    private String initDirectory(String dir) throws IOException {
        String resolvedDir = (dataDirectory == null || dataDirectory.isBlank()) ? System.getProperty("java.io.tmpdir") : dataDirectory;
        Path path = Path.of(resolvedDir, dir);
        Files.createDirectories(path);
        return path.toString();
    }

    public File getGeneratedContractFileName(String hashToFinalize) {
        return new File(workingDirectory, "contract_en_" + hashToFinalize + "_prepare.pdf");
    }

    public static String beVatPretty(String s) {
        if (s == null) return null;
        s = s.toUpperCase().replaceFirst("^BE", "").replaceAll("\\D", "");
        if (s.length() == 9) s = "0" + s;
        return s.length() == 10 ? s.replaceFirst("(\\d{4})(\\d{3})(\\d{3})", "$1.$2.$3") : s;
    }

    public byte[] generateFilledContract(Director director) {
        String company = director.getCompany().getName();
        String address = director.getCompany().getStreet() + " " + director.getCompany().getHouseNumber() + ", " + director.getCompany().getPostalCode() + " " + director.getCompany().getCity();
        String companyNumber = beVatPretty(director.getCompany().getVatNumber());
        String title = "Director";
        String representative = director.getName();

        try (InputStream resource = getClass().getResourceAsStream(LETS_PEPPOL_CONTRACT_TEMPLATE)) {
            if (resource == null)
                throw new FileNotFoundException("Classpath resource not found: " + LETS_PEPPOL_CONTRACT_TEMPLATE);
            byte[] bytes = resource.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(bytes)) {
                var info = doc.getDocumentInformation();
                if (info == null)
                    info = new PDDocumentInformation();
                info.setTitle("Let's Peppol Contract – " + director.getCompany().getName());
                info.setAuthor("Business Application Research Group Europe");
                info.setSubject("Let’s Peppol Service Agreement");
                doc.setDocumentInformation(info);
                PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
                if (form == null)
                    throw new IllegalStateException("Template has no AcroForm fields");
                form.getField("company").setValue(company);
                form.getField("identification").setValue(IDENTIFICATION_CONTENT.formatted(company, address, companyNumber, title, representative));
                form.getField("representative").setValue(representative);
                form.getField("title").setValue(title);
                form.refreshAppearances();   // PDFBox 3: regenerate form appearances
                form.flatten(); //final, non-form PDF

                try (var baos = new ByteArrayOutputStream()) {
                    doc.save(baos);
                    return baos.toByteArray();
                }
            }
        } catch (Exception e) {
            log.error("Error generating contract: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate contract for signing", e);
        }
    }

    public PrepareSigningResponse prepareSigning(PrepareSigningRequest request) {
        TokenVerificationResponse tokenVerificationResponse = activationService.verify(request.emailToken());
        Director director = getDirector(request.directorId(), tokenVerificationResponse);
        log.info("Preparing contract signing for company {} and email {}", tokenVerificationResponse.company().peppolId(), tokenVerificationResponse.email());

        byte[] generatedPdf = generateFilledContract(director);
        byte[] preparedPdfBytes;
        String hashToFinalize = request.sha256();
        File preparedPdf = getGeneratedContractFileName(hashToFinalize);
        X500Name x500Name;
        try (InputStream resource = new ByteArrayInputStream(generatedPdf);
             PdfReader pdfReader = new PdfReader(resource);
             OutputStream outputStream = new FileOutputStream(preparedPdf)) {

            X509Certificate[] chain = CertificateUtil.getCertificateChain(request.certificate());
            log.debug("Certificate chain loaded with {} certificates", chain.length);
            x500Name = CertificateUtil.getX500Name(chain);

            PdfSigner signer = new PdfSigner(pdfReader, outputStream, new StampingProperties().useAppendMode());

            String signatureContent = getSignatureContent(x500Name, director);
            SignerProperties signerProperties = getSignerProperties(signatureContent);
            signer.setSignerProperties(signerProperties);

            PreSignatureContainer external = new PreSignatureContainer();//(chain);
            signer.signExternalContainer(external, 16000);
            preparedPdfBytes = external.getHash();
        } catch (Exception e) {
            log.error("Error preparing contract for signing: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to prepare contract for signing", e);
        }

        String hash = Base64.getEncoder().encodeToString(preparedPdfBytes);
        log.info("Contract prepared for signing, hash length: {}", hash.length());
        return new PrepareSigningResponse(hash, hashToFinalize, HASH_ALGORITHM, isAllowedToSign(x500Name, director));
    }

    public static SignerProperties getSignerProperties(String signatureContent) throws IOException {
        var res = new ClassPathResource(SIGNING_FONTS);
        PdfFont font;
        try (var in = res.getInputStream()) {
            byte[] ttf = in.readAllBytes();
            FontProgram fp = FontProgramFactory.createFont(ttf);
            font = PdfFontFactory.createFont(fp, PdfEncodings.WINANSI);
        }

        var logoRes = new ClassPathResource(SIGNING_BACKGROUND_LOGO);
        PdfImageXObject logo = new PdfImageXObject(ImageDataFactory.create(logoRes.getInputStream().readAllBytes()));

        BackgroundSize backgroundSize = new BackgroundSize();
        backgroundSize.setBackgroundSizeToValues(new UnitValue(UnitValue.POINT, 240), new UnitValue(UnitValue.POINT, 100));

        BackgroundImage backgroundImage = new BackgroundImage.Builder()
                .setImage(logo)
                .setBackgroundSize(backgroundSize)
                .setBackgroundRepeat(new BackgroundRepeat(BackgroundRepeat.BackgroundRepeatValue.NO_REPEAT))
                .setBackgroundPosition(new BackgroundPosition().setYShift(new UnitValue(UnitValue.POINT, 30)))
                .build();

        SignatureFieldAppearance appearance = new SignatureFieldAppearance(SignerProperties.IGNORED_ID)
                .setContent(signatureContent)
                .setFont(font)
                .setFontSize(10)
                .setBorder(new SolidBorder(new DeviceRgb(0, 0, 0),1.0f))
                .setBackgroundImage(backgroundImage);

        return new SignerProperties()
                .setPageRect(new Rectangle(300, 350, 240, 160))
                .setPageNumber(18)
                .setSignatureAppearance(appearance)
                .setFieldName(SIGNING_FORMFIELD);
    }

    private static String getSignatureContent(X500Name x500Name, Director director) {
        String cn = getRDNName(x500Name, BCStyle.CN);
        String serialNumber = getRDNName(x500Name, BCStyle.SERIALNUMBER);
        String givenName = getRDNName(x500Name, BCStyle.GIVENNAME);
        String surName = getRDNName(x500Name, BCStyle.SURNAME);
        String name = cn;
        if (givenName != null && surName != null) {
            name = givenName + " " + surName;
        }
        return SIGNATURE_CONTENT.formatted(
                name,
                serialNumber,
                sdf.format(new Date()),
                director.getCompany().getName(),
                director.getCompany().getPeppolId(),
                director.getName()
        );
    }

    public static boolean isAllowedToSign(X500Name x500Name, Director director) {
        String givenName = getRDNName(x500Name, BCStyle.GIVENNAME);
        String surName = getRDNName(x500Name, BCStyle.SURNAME);
        String fullName = director.getName();
        return NameMatchUtil.matches(givenName, surName, fullName);
    }

    public byte[] finalizeSign(FinalizeSigningRequest signingRequest) {
        TokenVerificationResponse tokenVerificationResponse = activationService.verify(signingRequest.emailToken());
        Director director = getDirector(signingRequest.directorId(), tokenVerificationResponse);
        log.info("Finalizing contract signing for company {} and email {}", tokenVerificationResponse.company().peppolId(), tokenVerificationResponse.email());

        X509Certificate[] certificates;
        try {
            certificates = CertificateUtil.getCertificateChain(signingRequest.certificate());
        } catch (Exception e) {
            log.error("Error getting certificate chain for signing: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }

        byte[] finalPdfBytes = createFinalContract(certificates, signingRequest, tokenVerificationResponse);

        IdentityVerificationRequest identityVerificationRequest = new IdentityVerificationRequest(
                tokenVerificationResponse.email(),
                director,
                signingRequest.password(),
                signingRequest.signatureAlgorithm().toString(),
                signingRequest.hashToSign(),
                signingRequest.signature(),
                signingRequest.certificate(),
                certificates[0],
                CertificateUtil.getX500Name(certificates)
        );
        Account account = identityVerificationService.create(identityVerificationRequest);
        activationService.setVerified(signingRequest.emailToken());

        return writeContractToFile(tokenVerificationResponse.company().peppolId(), account, finalPdfBytes);
    }

    public byte[] getContract(String peppolId, Long accountId) {
        try {
            return Files.readAllBytes(Path.of(contractDirectory, "contract_%s_%d.pdf".formatted(peppolId.replace(':', '_'), accountId)));
        } catch (IOException e) {
            throw new RuntimeException("Error getting contract from file: " + e.getMessage(), e);
        }
    }

    private byte[] writeContractToFile(String peppolId, Account account, byte[] finalPdfBytes) {
        try {
            File finalizedPdf = new File(contractDirectory, "contract_%s_%d.pdf".formatted(peppolId.replace(':', '_'), account.getId()));
            Files.write(finalizedPdf.toPath(), finalPdfBytes);
            log.info("Contract signing completed successfully for company: {}, final contract size: {} bytes", peppolId, finalPdfBytes.length);
            return finalPdfBytes;
        } catch (Exception e) {
            log.error("Error writing contract with signature for company {}: {}", peppolId, e.getMessage(), e);
            throw new RuntimeException("Failed to finalize contract signature", e);
        }
    }

    private byte[] createFinalContract(X509Certificate[] certificates, FinalizeSigningRequest request, TokenVerificationResponse tokenVerificationResponse) {
        File preparedPdf = new File(workingDirectory, "contract_en_%s_prepare.pdf".formatted(request.hashToFinalize()));
        if (!preparedPdf.exists()) {
            throw new RuntimeException("FinalizeSigningRequest invalid");
        }

        try {
            log.debug("Using certificate chain with {} certificates for finalization", certificates.length);

            byte[] hash = Base64.getDecoder().decode(request.hashToSign());
            byte[] extSignature = Base64.getDecoder().decode(request.signature());
            log.debug("Hash length: {}, Signature length: {}", hash.length, extSignature.length);

            byte[] cmsSignature = EmbeddableSignatureUtil.wrapWebEidSignature(certificates, extSignature, hash);
            log.debug("Generated CMS signature with length: {}", cmsSignature.length);

            FinalizeSignatureContainer finalizeSignatureContainer = new FinalizeSignatureContainer(certificates, cmsSignature);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfSigner.signDeferred(new PdfReader(preparedPdf), SIGNING_FORMFIELD, outputStream, finalizeSignatureContainer);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error finalizing contract signature for company {}: {}", tokenVerificationResponse.company().peppolId(), e.getMessage(), e);
            throw new RuntimeException("Failed to finalize contract signature", e);
        }
    }

    public Director getDirector(Long directorId, TokenVerificationResponse tokenVerificationResponse) {
        Director director = directorRepository.findById(directorId).orElseThrow(() -> new RuntimeException("Invalid director"));
        if (!director.getCompany().getPeppolId().equals(tokenVerificationResponse.company().peppolId())) {
            log.error("Security alert, director {} company {} mismatch", director.getCompany().getPeppolId(), tokenVerificationResponse.company().peppolId());
            throw new RuntimeException("Invalid director");
        }
        return director;
    }
}
