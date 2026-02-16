package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.ConfirmCompanyRequest;
import org.letspeppol.kyc.dto.SimpleMessage;
import org.letspeppol.kyc.dto.TokenVerificationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.service.ActivationService;
import org.letspeppol.kyc.service.CompanyService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final ActivationService activationService;
    private final CompanyService companyService;

    /// *Registration step 1* Retrieves company info based on peppolId (= VAT number converted by UI) to confirm company information is correct to use
    @GetMapping("/company/{peppolId}")
    public CompanyResponse getCompany(@PathVariable String peppolId) {
        return companyService.getResponseByPeppolId(peppolId).orElseThrow(() -> new NotFoundException(KycErrorCodes.COMPANY_NOT_FOUND));
    }

    /// *Registration step 2* Sends verification email to confirm email address is correct to use after company info is confirmed to be correct
    @PostMapping("/confirm-company")
    public SimpleMessage confirmCompany(@RequestBody ConfirmCompanyRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        activationService.requestActivation(request, acceptLanguage);
        return new SimpleMessage("Activation email sent");
    }

    /// *Registration step 3* Verifies email address is correct and sends company information with list of directors to select the signing director
    @PostMapping("/verify")
    public TokenVerificationResponse verify(@RequestParam String token) {
        return activationService.verify(token);
    }

}
