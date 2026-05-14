package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.ConfirmCompanyRequest;
import org.letspeppol.kyc.dto.SetPasswordRequest;
import org.letspeppol.kyc.dto.SimpleMessage;
import org.letspeppol.kyc.dto.TokenVerificationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.service.ActivationService;
import org.letspeppol.kyc.service.CompanyService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
@Tag(name = "KYC Registration", description = "Endpoints for the public company onboarding flow, from company lookup through email confirmation and final account activation.")
public class RegistrationController {

    private final ActivationService activationService;
    private final CompanyService companyService;

    /// *Registration step 1* Retrieves company info based on peppolId (= VAT number converted by UI) to confirm company information is correct to use
    @GetMapping("/company/{peppolId}")
    @Operation(summary = "Load company for registration", description = "Looks up a company by Peppol identifier so the user can confirm they are registering the correct legal entity.")
    public CompanyResponse getCompany(@PathVariable String peppolId) {
        return companyService.getResponseByPeppolId(peppolId).orElseThrow(() -> new NotFoundException(KycErrorCodes.COMPANY_NOT_FOUND));
    }

    /// *Registration step 2* Sends verification email to confirm email address is correct to use after company info is confirmed to be correct
    @PostMapping("/confirm-company")
    @Operation(summary = "Start registration for a company", description = "Validates the selected company and sends the activation email that continues the onboarding flow.")
    public SimpleMessage confirmCompany(@RequestBody ConfirmCompanyRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        activationService.requestActivation(request, acceptLanguage);
        return new SimpleMessage("Activation email sent");
    }

    /// *Registration step 3* Verifies email address is correct and sends company information with list of directors to select the signing director
    @PostMapping("/verify")
    @Operation(summary = "Verify activation token", description = "Consumes the email verification token and returns the company context needed to continue with director selection and signing.")
    public TokenVerificationResponse verify(@RequestParam String token) {
        return activationService.verify(token);
    }

    /// *Registration step 7* Verifies the email token and sets the first password for an already signed account
    @PostMapping("/verify-account")
    @Operation(summary = "Activate account after signing", description = "Finalizes account creation by validating the token received after signing and storing the first password.")
    public void verifyAccount(@RequestBody SetPasswordRequest request) {
        EmailVerification verification = activationService.verifyAccount(request.token(), request.newPassword());
        if (verification.getType() == AccountType.AFFILIATE) {
            activationService.linkAffiliateOwnership(verification);
        }
    }

}
