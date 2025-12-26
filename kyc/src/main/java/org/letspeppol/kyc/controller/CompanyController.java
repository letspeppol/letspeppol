package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.AccountInfo;
import org.letspeppol.kyc.dto.CompanySearchResponse;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.CompanyService;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.SigningService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sapi/company")
@RequiredArgsConstructor
public class CompanyController {

    private final AccountService accountService;
    private final CompanyService companyService;
    private final JwtService jwtService;
    private final SigningService signingService;

    /// Retrieves account info based on valid JWT token, used by App when peppolId is unknown on getCompany (called by UI right after obtaining JWT token)
    @GetMapping
    public ResponseEntity<?> getAccountForToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        UUID uid = jwtService.validateAndGetInfo(authHeader).uid();
        Account account = accountService.getByExternalId(uid);
        AccountInfo accountInfo = new AccountInfo(
                account.getCompany().getPeppolId(),
                account.getCompany().getVatNumber(),
                account.getCompany().getName(),
                account.getCompany().getStreet(),
                account.getCompany().getCity(),
                account.getCompany().getPostalCode(),
                account.getName(),
                account.getEmail()
        );
        return ResponseEntity.ok(accountInfo);
    }

    @GetMapping("/search")
    public ResponseEntity<List<CompanySearchResponse>> search(
            @RequestParam(value = "vatNumber", required = false) String vatNumber,
            @RequestParam(value = "peppolId", required = false) String peppolId,
            @RequestParam(value = "companyName", required = false) String companyName) {
        return ResponseEntity.ok(companyService.search(vatNumber, peppolId, companyName));
    }

    /// Registers peppolId on the Peppol Directory, must call Proxy to register on AP
    @PostMapping("/peppol/register")
    public ResponseEntity<?> register(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        boolean peppolActive = companyService.registerCompany(jwtInfo.peppolId());
        if (jwtInfo.peppolActive() == peppolActive) {
            if (peppolActive) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body("Access Point registration failed; state unchanged.");
        }
        String token = jwtService.generateToken(
                jwtInfo.peppolId(),
                peppolActive,
                jwtInfo.uid()
        );
        return ResponseEntity.ok(token);
    }

    /// Unregisters (not deleting) peppolId from the Peppol Directory, must call Proxy to unregister from AP
    @PostMapping("/peppol/unregister")
    public ResponseEntity<?> unregister(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        boolean peppolActive = companyService.unregisterCompany(jwtInfo.peppolId());
        if (jwtInfo.peppolActive() == peppolActive) {
            if (!peppolActive) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body("Access Point unregistration failed; state unchanged.");
        }
        String token = jwtService.generateToken(
                jwtInfo.peppolId(),
                peppolActive,
                jwtInfo.uid()
        );
        return ResponseEntity.ok(token);
    }

    /// Download signed contract saved for peppolId
    @GetMapping("signed-contract")
    public ResponseEntity<?> signedContract(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String peppolId = jwtService.validateAndGetInfo(authHeader).peppolId();
        UUID externalId = jwtService.validateAndGetInfo(authHeader).uid();
        Account account = accountService.getByExternalId(externalId);
        byte[] data = signingService.getContract(peppolId, account.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract_signed.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
