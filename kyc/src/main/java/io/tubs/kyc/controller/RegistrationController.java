package io.tubs.kyc.controller;

import io.tubs.kyc.dto.ConfirmCompanyRequest;
import io.tubs.kyc.dto.SimpleMessage;
import io.tubs.kyc.dto.TokenVerificationResponse;
import io.tubs.kyc.exception.KycErrorCodes;
import io.tubs.kyc.exception.KycException;
import io.tubs.kyc.service.ActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final ActivationService activationService;

    @PostMapping("/confirm-company")
    public SimpleMessage confirmCompany(@RequestBody ConfirmCompanyRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        activationService.requestActivation(request, acceptLanguage);
        return new SimpleMessage("Activation email sent (if delivery fails, check logs for link)");
    }

    @GetMapping("/contract")
    public ResponseEntity<byte[]> getContract() {
        try (var inputStream = getClass().getResourceAsStream("/docs/contract_en.pdf")) {
            if (inputStream == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new KycException(KycErrorCodes.CONTRACT_NOT_FOUND);
        }
    }

    @PostMapping("/verify")
    public TokenVerificationResponse verify(@RequestParam String token) {
        return activationService.verify(token);
    }

}
