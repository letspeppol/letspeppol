package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.service.ActivationService;
import org.letspeppol.kyc.service.SigningService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class IdentityVerificationController {

    private final SigningService signingService;
    private final ActivationService activationService;

    /// *Registration step 4* Generates contract hashes as preparation used for signing with Web eID (pdf gets a temporary signature placeholder). This happens right after the "Select a certificate" and before the "Signing" steps of Web eID
    @PostMapping("/sign/prepare")
    public PrepareSigningResponse prepare(@RequestBody PrepareSigningRequest request) {
        return signingService.prepareSigning(request);
    }

    /// *Registration step 5* Generates contract for selected (i.e. step 4) director to be signed
    @GetMapping("/contract/{directorId}")
    public ResponseEntity<byte[]> getContract(@PathVariable Long directorId, @RequestParam String token) {
        EmailVerification emailVerification = activationService.getValidTokenInformation(token);
        Director director = signingService.getDirector(directorId, emailVerification.getPeppolId());
        byte[] preparedPdf = signingService.generateFilledContract(director);
        if (preparedPdf == null || preparedPdf.length == 0) {
            throw new KycException(KycErrorCodes.CONTRACT_NOT_FOUND);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("contract_en.pdf", StandardCharsets.UTF_8).build().toString())
                .body(preparedPdf);
    }

    /// *Registration step 6* Signs contract by selected director and used Web eID during "Signing" step and sends certificate information to store and generate signed contract
    @PostMapping("/sign/finalize")
    public ResponseEntity finalize(@RequestBody FinalizeSigningRequest request) {
        FinalizeSigningResponse finalizeSigningResponse = signingService.finalizeSign(request);
        String status;
        RegistrationResponse registrationResponse = finalizeSigningResponse.registrationResponse();
        if (registrationResponse == null) {
            status = "UNKNOWN";
        } else if (!registrationResponse.peppolActive()) {
            status = switch (registrationResponse.errorCode()) {
                case KycErrorCodes.PROXY_REGISTRATION_CONFLICT -> "CONFLICT";
                case KycErrorCodes.PROXY_REGISTRATION_SUSPENDED -> "SUSPENDED";
                case KycErrorCodes.PROXY_REGISTRATION_FAILED,
                     KycErrorCodes.PROXY_UNREGISTRATION_FAILED,
                     KycErrorCodes.PROXY_FAILED,
                     KycErrorCodes.PROXY_REGISTRATION_INTERNAL_ERROR,
                     KycErrorCodes.PROXY_REGISTRATION_UNAVAILABLE -> "FAILED";
                default -> "UNKNOWN";
            };
        } else {
            status = "OK";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract_signed.pdf")
                .headers(headers -> {
                    headers.add("Registration-Status", status);
                    if (registrationResponse != null && Objects.equals(registrationResponse.errorCode(), KycErrorCodes.PROXY_REGISTRATION_CONFLICT)) headers.add("Registration-Provider", UriUtils.encode(registrationResponse.body(), StandardCharsets.UTF_8));
                })
                .contentType(MediaType.APPLICATION_PDF)
                .body(finalizeSigningResponse.pdfBytes());
    }
}
