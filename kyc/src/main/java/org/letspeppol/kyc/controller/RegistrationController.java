package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.ConfirmCompanyRequest;
import org.letspeppol.kyc.dto.SimpleMessage;
import org.letspeppol.kyc.dto.TokenVerificationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.service.ActivationService;
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

    /// *Registration step 2* Sends verification email to confirm email address is correct to use after company info is confirmed to be correct
    @PostMapping("/confirm-company")
    public SimpleMessage confirmCompany(@RequestBody ConfirmCompanyRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        activationService.requestActivation(request, acceptLanguage);
        return new SimpleMessage("Activation email sent (if delivery fails, check logs for link)");
    }

    /// *Registration step 4* Generates contract for selected director to be signed
    @GetMapping("/contract")
    public ResponseEntity<byte[]> getContract() { //TODO : send selected director
        try (var inputStream = getClass().getResourceAsStream("/docs/contract_en.pdf")) { //TODO : generate with company name and user name
            if (inputStream == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new KycException(KycErrorCodes.CONTRACT_NOT_FOUND);
        }
    }

    /// *Registration step 3* Verifies email address is correct and sends company information with list of directors to select the signing director
    @PostMapping("/verify")
    public TokenVerificationResponse verify(@RequestParam String token) {
        return activationService.verify(token);
    }

}
