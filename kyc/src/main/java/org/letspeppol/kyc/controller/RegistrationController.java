package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.ConfirmCompanyRequest;
import org.letspeppol.kyc.dto.SimpleMessage;
import org.letspeppol.kyc.dto.TokenVerificationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.service.ActivationService;
import org.letspeppol.kyc.service.SigningService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final ActivationService activationService;
    private final SigningService signingService;

    /// *Registration step 2* Sends verification email to confirm email address is correct to use after company info is confirmed to be correct
    @PostMapping("/confirm-company")
    public SimpleMessage confirmCompany(@RequestBody ConfirmCompanyRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        activationService.requestActivation(request, acceptLanguage);
        return new SimpleMessage("Activation email sent (if delivery fails, check logs for link)");
    }

    /// *Registration step 4* Generates contract for selected director to be signed
    @GetMapping("/contract/{directorId}")
    public ResponseEntity<byte[]> getContract(@PathVariable Long directorId, @RequestParam String token) {
        TokenVerificationResponse tokenVerificationResponse = activationService.verify(token);
        Director director = signingService.getDirector(directorId, tokenVerificationResponse);
        byte[] preparedPdf = signingService.generateFilledContract(director);
        if (preparedPdf == null || preparedPdf.length == 0) {
            throw new KycException(KycErrorCodes.CONTRACT_NOT_FOUND);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("contract_en.pdf", StandardCharsets.UTF_8).build().toString())
                .body(preparedPdf);
    }

    /// *Registration step 3* Verifies email address is correct and sends company information with list of directors to select the signing director
    @PostMapping("/verify")
    public TokenVerificationResponse verify(@RequestParam String token) {
        return activationService.verify(token);
    }

}
