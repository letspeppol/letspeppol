package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.FinalizeSigningRequest;
import org.letspeppol.kyc.dto.PrepareSigningRequest;
import org.letspeppol.kyc.dto.PrepareSigningResponse;
import org.letspeppol.kyc.service.SigningService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class IdentityVerificationController {

    private final SigningService signingService;

    /// *Registration step 4* Generates contract hashes as preparation used for signing with Web eID (pdf gets a temporary signature placeholder). This happens right after the "Select a certificate" and before the "Signing" steps of Web eID
    @PostMapping("/sign/prepare")
    public PrepareSigningResponse prepare(@RequestBody PrepareSigningRequest request) {
        return signingService.prepareSigning(request);
    }

    /// *Registration step 6* Signs contract by selected director and used Web eID during "Signing" step and sends certificate information to store and generate signed contract
    @PostMapping("/sign/finalize")
    public ResponseEntity finalize(@RequestBody FinalizeSigningRequest request) {
        byte[] data = signingService.finalizeSign(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract_signed.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
