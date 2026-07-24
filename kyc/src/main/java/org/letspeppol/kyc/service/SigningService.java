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
import io.micrometer.core.instrument.Counter;
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
import org.letspeppol.kyc.service.signing.SignatureUtil;
import org.letspeppol.kyc.util.NameMatchUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.security.KeyStore;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidator;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509CertSelector;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

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
    private final Counter prepareSigningCounter;
    private final Counter finalizeSigningCounter;

    @Value("${contract.data.dir:#{null}}")
    private String dataDirectory;

    private String workingDirectory;
    private String contractDirectory;

    // Maps a prepared-signing id (hashToFinalize) to the exact digest the server prepared for signing.
    // Binds the eID signature to OUR contract and makes a prepared signing single-use (anti-replay).
    private final ConcurrentHashMap<String, String> preparedHashes = new ConcurrentHashMap<>();

    @Value("${webeid.trusted-ca-truststore:#{null}}")
    private String trustedCaTruststore;
    @Value("${webeid.trusted-ca-truststore-password:#{null}}")
    private String trustedCaTruststorePassword;
    @Value("${webeid.check-revocation:false}")
    private boolean checkRevocation;

    private KeyStore trustedCaKeyStore;

    @PostConstruct
    public void init() throws IOException {
        workingDirectory = initDirectory( "/temp");
        contractDirectory = initDirectory( "/contracts");
        loadTrustedCaKeyStore();
    }

    /**
     * Load the optional eID truststore once at startup. A configured store must be readable, be a
     * JKS, and contain at least one self-signed X.509 root; otherwise KYC must not start with a
     * silently disabled or ineffective trust policy.
     */
    private void loadTrustedCaKeyStore() {
        if (trustedCaTruststore == null || trustedCaTruststore.isBlank()) {
            return;
        }
        try (InputStream in = new FileInputStream(trustedCaTruststore)) {
            // This setting deliberately accepts a JKS file. Do not use the JVM default here: since
            // Java 9 that default is usually PKCS12, which makes the configured file format implicit.
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(in, trustedCaTruststorePassword == null ? null : trustedCaTruststorePassword.toCharArray());

            boolean containsRoot = false;
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                java.security.cert.Certificate certificate = keyStore.getCertificate(aliases.nextElement());
                if (certificate instanceof X509Certificate x509Certificate && isSelfSigned(x509Certificate)) {
                    containsRoot = true;
                    break;
                }
            }
            if (!containsRoot) {
                throw new IllegalStateException("contains no self-signed X.509 trust root");
            }
            trustedCaKeyStore = keyStore;
            log.info("Loaded Web-eID truststore {} with {} entries", trustedCaTruststore, keyStore.size());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load configured Web-eID truststore '" + trustedCaTruststore + "'", e);
        }
    }

    private String initDirectory(String dir) throws IOException {
        String resolvedDir = (dataDirectory == null || dataDirectory.isBlank()) ? System.getProperty("java.io.tmpdir") : dataDirectory;
        Path path = Path.of(resolvedDir, dir);
        Files.createDirectories(path);
        return path.toString();
    }

    // Strict base64 charset only; the '/' and '+' base64 may contain are mapped to filesystem-safe
    // characters and the charset rules out path separators / '..' traversal in the contract filename.
    private static final Pattern SAFE_HASH = Pattern.compile("^[A-Za-z0-9+/=]{1,64}$");

    private static String safeHashName(String hashToFinalize) {
        if (hashToFinalize == null || !SAFE_HASH.matcher(hashToFinalize).matches()) {
            throw new RuntimeException("Invalid hashToFinalize");
        }
        return hashToFinalize.replace('/', '_').replace('+', '-').replace("=", "");
    }

    public File getGeneratedContractFileName(String hashToFinalize) {
        return new File(workingDirectory, "contract_en_" + safeHashName(hashToFinalize) + "_prepare.pdf");
    }

    public static String beVatPretty(String s) {
        if (s == null) return null;
        s = s.toUpperCase().replaceFirst("^BE", "").replaceAll("\\D", "");
        if (s.length() == 9) s = "0" + s;
        return s.length() == 10 ? s.replaceFirst("(\\d{4})(\\d{3})(\\d{3})", "$1.$2.$3") : s;
    }

    public byte[] generateFilledContract(Director director) {
        String company = director.getCompany().getName();
        String address = director.getCompany().getStreet() + ", " + director.getCompany().getPostalCode() + " " + director.getCompany().getCity();
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
                info.setTitle("Let’s Peppol Contract – " + director.getCompany().getName());
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
        prepareSigningCounter.increment();
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
        preparedHashes.put(safeHashName(hashToFinalize), hash);
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

    public FinalizeSigningResponse finalizeSign(FinalizeSigningRequest signingRequest) {
        finalizeSigningCounter.increment();
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

        // SECURITY: (1) validate the certificate (temporal validity always; full chain-to-trusted-eID-root
        // when 'webeid.trusted-ca-truststore' is configured), (2) cryptographically verify the Web-eID
        // signature, and (3) bind it to the exact digest the server prepared for THIS contract (anti-replay,
        // single-use). Previously arbitrary signature bytes with any (self-signed) certificate were accepted.
        // NOTE: full chain validation requires the eID/QTSP root CAs in the configured truststore; without it
        // only signature validity + temporal validity are enforced. Adopting eu.webeid.security:authtoken-validator
        // remains the recommended definitive solution (bundles the trust anchors + nonce ceremony).
        validateCertificateChain(certificates);
        byte[] signedDigest = Base64.getDecoder().decode(signingRequest.hashToSign());
        byte[] eidSignature = Base64.getDecoder().decode(signingRequest.signature());
        if (!SignatureUtil.verifyWebEidSignature(certificates[0], signedDigest, eidSignature)) {
            log.error("eID signature verification FAILED for company {} email {}",
                    tokenVerificationResponse.company().peppolId(), tokenVerificationResponse.email());
            throw new RuntimeException("eID signature verification failed");
        }
        String expectedDigest = preparedHashes.remove(safeHashName(signingRequest.hashToFinalize()));
        if (expectedDigest == null || !expectedDigest.equals(signingRequest.hashToSign())) {
            log.error("eID prepared-digest mismatch / replay for company {}", tokenVerificationResponse.company().peppolId());
            throw new RuntimeException("eID signature does not match a freshly prepared contract");
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
        IdentityVerificationResponse identityVerificationResponse = identityVerificationService.createAdmin(identityVerificationRequest);
        activationService.setVerified(signingRequest.emailToken());

        return new FinalizeSigningResponse(writeContractToFile(tokenVerificationResponse.company().peppolId(), identityVerificationResponse.account(), finalPdfBytes), identityVerificationResponse.registrationResponse());
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
        File preparedPdf = getGeneratedContractFileName(request.hashToFinalize());
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

    /**
     * Validates the eID certificate: always checks temporal validity; when a truststore of trusted
     * eID/QTSP root CAs is configured ('webeid.trusted-ca-truststore'), also validates that the
     * certificate chains to one of those roots (rejects self-signed / forged certificates). When no
     * truststore is configured the chain is NOT validated (a prominent warning is logged) — set it in
     * production. This is fail-open-without-config so it never breaks the current flow.
     */
    private void validateCertificateChain(X509Certificate[] chain) {
        if (chain == null || chain.length == 0) {
            throw new RuntimeException("Empty certificate chain");
        }
        try {
            chain[0].checkValidity();
        } catch (Exception e) {
            throw new RuntimeException("eID certificate is expired or not yet valid", e);
        }
        boolean[] keyUsage = chain[0].getKeyUsage();
        if (keyUsage != null && keyUsage.length >= 2 && !keyUsage[0] && !keyUsage[1]) {
            // Neither digitalSignature (0) nor nonRepudiation (1): not a usable eID signing certificate.
            throw new RuntimeException("eID certificate is not valid for signing (key usage)");
        }
        if (trustedCaTruststore == null || trustedCaTruststore.isBlank()) {
            log.warn("eID certificate chain validation DISABLED — set 'webeid.trusted-ca-truststore' " +
                    "(Belgian eID / QTSP root CAs) in production to reject self-signed / forged certificates.");
            return;
        }
        try {
            // A configured store is loaded and structurally checked during startup, so this path
            // cannot silently fall back to an empty/unreadable store during signature validation.
            KeyStore ks = trustedCaKeyStore;
            if (ks == null) {
                throw new IllegalStateException("Configured Web-eID truststore was not initialized");
            }
            Set<TrustAnchor> anchors = new HashSet<>();
            java.util.List<java.security.cert.Certificate> caCerts = new java.util.ArrayList<>();
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                java.security.cert.Certificate c = ks.getCertificate(aliases.nextElement());
                if (c instanceof X509Certificate xc) {
                    if (isSelfSigned(xc)) {
                        anchors.add(new TrustAnchor(xc, null));
                    } else {
                        // An issuing CA helps construct the path, but is not itself a trust anchor.
                        // Otherwise a compromised/intermediate CA would be accepted without proving
                        // its chain to one of the configured Belgian eID roots.
                        caCerts.add(xc);
                    }
                }
            }
            X509CertSelector target = new X509CertSelector();
            target.setCertificate(chain[0]);
            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, target);
            // Web-eID supplies only the end-entity certificate. Build its path from the issuing
            // CAs in the truststore; the JDK does not AIA-chase missing intermediates by default.
            params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(caCerts)));
            if (checkRevocation) {
                // Opt-in OCSP/CRL revocation checking (needs network access + the certs' AIA/CDP entries).
                params.addCertPathChecker((PKIXRevocationChecker) CertPathValidator.getInstance("PKIX").getRevocationChecker());
                params.setRevocationEnabled(true);
            } else {
                params.setRevocationEnabled(false);
            }
            CertPathBuilder.getInstance("PKIX").build(params);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("eID certificate chain is NOT trusted: {}", e.getMessage());
            throw new RuntimeException("eID certificate chain is not trusted", e);
        }
    }

    private static boolean isSelfSigned(X509Certificate certificate) {
        if (!certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
            return false;
        }
        try {
            certificate.verify(certificate.getPublicKey());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
