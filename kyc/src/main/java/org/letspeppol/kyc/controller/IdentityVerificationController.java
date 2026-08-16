package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.service.SigningService;
import org.letspeppol.kyc.service.SigningSessionService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
@Tag(name = "KYC Identity Verification", description = "Endpoints used during the director signing flow, including contract generation and Web eID signature finalization.")
public class IdentityVerificationController {

    private final SigningService signingService;

    /// *Registration step 4* Generates contract hashes as preparation used for signing with Web eID (pdf gets a temporary signature placeholder). This happens right after the "Select a certificate" and before the "Signing" steps of Web eID
    @PostMapping("/sign/prepare")
    @Operation(summary = "Prepare contract signing", description = "Builds the contract and returns a short-lived opaque signing session token kept only in UI memory. The authenticated eID holder may sign on behalf of the selected director/company; both identities are retained in the contract and audit record.")
    public PrepareSigningResponse prepare(@RequestBody PrepareSigningRequest request) {
        return signingService.prepareSigning(request);
    }

    /// *Registration step 5* Generates contract for selected (i.e. step 4) director to be signed
    @GetMapping("/contract/{peppolId}/{directorId}")
    @Operation(summary = "Download unsigned contract", description = "Returns the exact prepared contract bound to the short-lived X-Signing-Session capability. Missing, expired, unknown, or company/director-mismatched sessions receive the same not-found response.")
    public ResponseEntity<byte[]> getContract(
            @PathVariable String peppolId,
            @PathVariable Long directorId,
            @RequestHeader(name = SigningSessionService.HEADER_NAME, required = false) String signingSessionToken) {
        byte[] preparedPdf = signingService.getPreparedContract(peppolId, directorId, signingSessionToken);
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
    @Operation(summary = "Finalize contract signing", description = "Consumes the short-lived X-Signing-Session capability, verifies that company, director, eID certificate, prepared digest and file identifier all match, then persists the signed contract. A consumed session cannot be replayed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed contract PDF with registration status headers", content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    })
    public ResponseEntity<byte[]> finalize(
            @RequestBody FinalizeSigningRequest request,
            @RequestHeader(name = SigningSessionService.HEADER_NAME, required = false) String signingSessionToken) {
        FinalizeSigningResponse finalizeSigningResponse = signingService.finalizeSign(request, signingSessionToken);
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
