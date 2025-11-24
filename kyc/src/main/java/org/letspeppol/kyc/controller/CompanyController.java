package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.AccountInfo;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.CompanyService;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.SigningService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final AccountService accountService;
    private final CompanyService companyService;
    private final JwtService jwtService;
    private final SigningService signingService;

    /// *Registration step 1* Retrieves company info based on peppolId (= VAT number converted by UI) to confirm company information is correct to use
    @GetMapping("{peppolId}")
    public CompanyResponse getCompany(@PathVariable String peppolId) {
         return companyService.getByPeppolId(peppolId).orElseThrow(() -> new NotFoundException(KycErrorCodes.COMPANY_NOT_FOUND));
    }

    /// Retrieves account info based on valid JWT token, used by App when peppolId is unknown on getCompany (called by UI right after obtaining JWT token)
    @GetMapping
    public ResponseEntity<?> getAccountForToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        UUID uid = UUID.fromString(jwtService.validateAndGetInfo(authHeader).uid());
        Account account = accountService.getByExternalId(uid);
        AccountInfo accountInfo = new AccountInfo(
                account.getCompany().getPeppolId(),
                account.getCompany().getVatNumber(),
                account.getCompany().getName(),
                account.getCompany().getStreet(),
                account.getCompany().getHouseNumber(),
                account.getCompany().getCity(),
                account.getCompany().getPostalCode(),
                account.getName(),
                account.getEmail()
        );
        return ResponseEntity.ok(accountInfo);
    }

    /// Unregisters (not deleting) peppolId from the Peppol Directory, must call Proxy to unregister from AP
    @PostMapping("unregister")
    public ResponseEntity<?> unregister(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        companyService.unregisterCompany(jwtInfo.peppolId(), jwtInfo.token());
        return ResponseEntity.ok().build();
    }

    /// Download signed contract saved for peppolId
    @GetMapping("signed-contract")
    public ResponseEntity<?> signedContract(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String peppolId = jwtService.validateAndGetInfo(authHeader).peppolId();
        byte[] data = signingService.getContract(peppolId, 0L); // TODO
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract_signed.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);

    }
}
