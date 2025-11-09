package io.tubs.kyc.controller;

import io.tubs.kyc.dto.CompanyResponse;
import io.tubs.kyc.exception.KycErrorCodes;
import io.tubs.kyc.exception.NotFoundException;
import io.tubs.kyc.service.CompanyService;
import io.tubs.kyc.service.JwtService;
import io.tubs.kyc.service.SigningService;
import io.tubs.kyc.service.jwt.JwtInfo;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/{companyNumber}")
    public CompanyResponse getCompany(@PathVariable String companyNumber) {
         return companyService.getByCompanyNumber(companyNumber).orElseThrow(() -> new NotFoundException(KycErrorCodes.COMPANY_NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<?> getCompanyForToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String companyNumber = jwtService.validateAndGetInfo(authHeader).companyNumber();
        return ResponseEntity.ok(companyService.getByCompanyNumber(companyNumber));
    }

    @PostMapping("/unregister")
    public ResponseEntity<?> unregister(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        companyService.unregisterCompany(jwtInfo.companyNumber(), jwtInfo.token());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/signed-contract")
    public ResponseEntity<?> signedContract(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String companyNumber = jwtService.validateAndGetInfo(authHeader).companyNumber();
        byte[] data = signingService.getContract(companyNumber, 0L); // TODO
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract_signed.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);

    }
}
