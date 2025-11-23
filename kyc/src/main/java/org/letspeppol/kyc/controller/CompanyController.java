package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.service.CompanyService;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.SigningService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final JwtService jwtService;
    private final SigningService signingService;

    @GetMapping("{peppolId}")
    public CompanyResponse getCompany(@PathVariable String peppolId) {
         return companyService.getByPeppolId(peppolId).orElseThrow(() -> new NotFoundException(KycErrorCodes.COMPANY_NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<?> getCompanyForToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String peppolId = jwtService.validateAndGetInfo(authHeader).peppolId();
        return ResponseEntity.ok(companyService.getByPeppolId(peppolId));
    }

    @PostMapping("unregister")
    public ResponseEntity<?> unregister(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        companyService.unregisterCompany(jwtInfo.peppolId(), jwtInfo.token());
        return ResponseEntity.ok().build();
    }

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
