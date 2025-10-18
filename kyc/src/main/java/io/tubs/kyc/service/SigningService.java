package io.tubs.kyc.service;

import com.itextpdf.forms.form.element.SignatureFieldAppearance;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.SignerProperties;
import io.tubs.kyc.dto.*;
import io.tubs.kyc.model.User;
import io.tubs.kyc.model.kbo.Director;
import io.tubs.kyc.repository.DirectorRepository;
import io.tubs.kyc.service.signing.CertificateUtil;
import io.tubs.kyc.service.signing.EmbeddableSignatureUtil;
import io.tubs.kyc.service.signing.FinalizeSignatureContainer;
import io.tubs.kyc.service.signing.PreSignatureContainer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.springframework.beans.factory.annotation.Value;
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

import static io.tubs.kyc.service.signing.CertificateUtil.getRDNName;

@Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class SigningService {

    private final ActivationService activationService;

    public static final String SIGNATURE_CONTENT = "Signed by %s\n%s\nTimestamp: %s\nCompany: %s\nDirector: %s";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    private final IdentityVerificationService identityVerificationService;
    private final DirectorRepository directorRepository;

    @Value("${contract.work.dir:#{null}}")
    private String workingDirectory;
    @Value("${contract.store.dir:#{null}}")
    private String contractDirectory;

    @PostConstruct
    public void init() throws IOException {
        workingDirectory = initDirectory(workingDirectory, "/kyc/temp");
        contractDirectory = initDirectory(contractDirectory, "/kyc/contracts");
    }

    private String initDirectory(String dir, String defaultSubdir) throws IOException {
        String resolvedDir = (dir == null) ? System.getProperty("java.io.tmpdir") + defaultSubdir : dir;
        Files.createDirectories(Path.of(resolvedDir));
        return resolvedDir;
    }

    public PrepareSigningResponse prepareSigning(PrepareSigningRequest request) {
        TokenVerificationResponse tokenVerificationResponse = activationService.verify(request.emailToken());
        identityVerificationService.verifyNotRegistered(tokenVerificationResponse.email());
        log.info("Preparing PDF signing for company {} and email {}", tokenVerificationResponse.company().companyNumber(), tokenVerificationResponse.email());
        Director director = getDirectory(request.directorId(), tokenVerificationResponse);

        byte[] preparedPdfBytes;
        String hashToFinalize = request.sha256();
        File preparedPdf = new File(workingDirectory, "contract_en_" + hashToFinalize + "_prepare.pdf");
        try (InputStream resource = getClass().getResourceAsStream("/docs/contract_en.pdf");
            PdfReader pdfReader = new PdfReader(resource);
            OutputStream outputStream = new FileOutputStream(preparedPdf)) {

            X509Certificate[] chain = CertificateUtil.getCertificateChain(request.certificate());
            log.debug("Certificate chain loaded with {} certificates", chain.length);

            PdfSigner signer = new PdfSigner(pdfReader, outputStream, new StampingProperties().useAppendMode());

            String signatureContent = getSignatureContent(chain, director);
            SignerProperties signerProperties = getSignerProperties(signatureContent);
            signer.setSignerProperties(signerProperties);

            PreSignatureContainer external = new PreSignatureContainer(chain);
            signer.signExternalContainer(external, 16000);
            preparedPdfBytes = external.getHash();
        } catch (Exception e) {
            log.error("Error preparing PDF for signing: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to prepare PDF for signing", e);
        }

        String hash = Base64.getEncoder().encodeToString(preparedPdfBytes);
        log.info("PDF prepared for signing, hash length: {}", hash.length());
        return new PrepareSigningResponse(hash, hashToFinalize, HASH_ALGORITHM);
    }

    public static SignerProperties getSignerProperties(String signatureContent) throws IOException {
        PdfFont font = PdfFontFactory.createFont(
                "src/main/resources/fonts/DejaVuSans.ttf",
                PdfEncodings.WINANSI
        );

        SignatureFieldAppearance appearance = new SignatureFieldAppearance(SignerProperties.IGNORED_ID).setContent(signatureContent);
        appearance.setFont(font);
        appearance.setFontSize(12);
        appearance.setBorder(new SolidBorder(new DeviceRgb(0, 0, 0),1.0f));

        SignerProperties signerProperties = new SignerProperties();
        signerProperties.setPageRect(new Rectangle(50, 20, 250, 130));
        signerProperties.setPageNumber(2);
        signerProperties.setSignatureAppearance(appearance);
        signerProperties.setFieldName("Signature1");
        return signerProperties;
    }

    private String getSignatureContent(X509Certificate[] chain, Director director) {
        X500Principal principal = new X500Principal(chain[0].getSubjectX500Principal().getEncoded());
        X500Name x500Name = new X500Name(principal.getName());
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
                director.getCompany().getCompanyNumber(),
                director.getName()
        );
    }

    public byte[] finalizeSign(FinalizeSigningRequest signingRequest) {
        TokenVerificationResponse tokenVerificationResponse = activationService.verify(signingRequest.emailToken());
        log.info("Finalizing PDF signing for company {} and email {}", tokenVerificationResponse.company().companyNumber(), tokenVerificationResponse.email());

        Director director = getDirectory(signingRequest.directorId(), tokenVerificationResponse);

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
                certificates[0]
        );
        User user = identityVerificationService.create(identityVerificationRequest);
        activationService.setVerified(signingRequest.emailToken());

        return writeContractToFile(tokenVerificationResponse.company().companyNumber(), user, finalPdfBytes);
    }

    public byte[] getContract(String companyNumber, Long userId) {
        try {
            return Files.readAllBytes(Path.of(contractDirectory, "contract_%s_%d.pdf".formatted(companyNumber, userId)));
        } catch (IOException e) {
            throw new RuntimeException("Error getting contract from file: " + e.getMessage(), e);
        }
    }

    private byte[] writeContractToFile(String companyNumber, User user, byte[] finalPdfBytes) {
        try {
            File finalizedPdf = new File(contractDirectory, "contract_%s_%d.pdf".formatted(companyNumber, user.getId()));
            Files.write(finalizedPdf.toPath(), finalPdfBytes);
            log.info("PDF signing completed successfully for company: {}, final PDF size: {} bytes", companyNumber, finalPdfBytes.length);
            return finalPdfBytes;
        } catch (Exception e) {
            log.error("Error writing PDF with signature for company {}: {}", companyNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to finalize PDF signature", e);
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
            PdfSigner.signDeferred(new PdfReader(preparedPdf), "Signature1", outputStream, finalizeSignatureContainer);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error finalizing PDF signature for company {}: {}", tokenVerificationResponse.company().companyNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to finalize PDF signature", e);
        }
    }

    private Director getDirectory(Long request, TokenVerificationResponse tokenVerificationResponse) {
        Director director = directorRepository.findById(request).orElseThrow(() -> new RuntimeException("Invalid director"));
        if (!director.getCompany().getCompanyNumber().equals(tokenVerificationResponse.company().companyNumber())) {
            log.error("Security alert, director {} company {} mismatch", director.getCompany().getCompanyNumber(), tokenVerificationResponse.company().companyNumber());
            throw new RuntimeException("Invalid director");
        }
        return director;
    }
}
