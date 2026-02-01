package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.AccountInfo;
import org.letspeppol.kyc.dto.CompanySearchResponse;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.exception.ForbiddenException;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.mapper.AccountMapper;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
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
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        Account account = (jwtInfo.accountType() == AccountType.ADMIN) ? accountService.getAdminByExternalId(jwtInfo.uid()) : accountService.getAdminByPeppolId(jwtInfo.peppolId());
        return ResponseEntity.ok(AccountMapper.toAccountInfo(account));  //This will be the ADMIN account and thus the one who signed the contract
    }

    @GetMapping("/search")
    public ResponseEntity<List<CompanySearchResponse>> search(
            @RequestParam(value = "vatNumber", required = false) String vatNumber,
            @RequestParam(value = "peppolId", required = false) String peppolId,
            @RequestParam(value = "companyName", required = false) String companyName) {
        return ResponseEntity.ok(companyService.search(vatNumber, peppolId, companyName)); //TODO : not really using the JWT, do we need to validate ? Also no comment :-o
    }

    /// Registers peppolId on the Peppol Directory, must call Proxy to register on AP
    @PostMapping("/peppol/register")
    public ResponseEntity<?> register(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        RegistrationResponse registrationResponse = companyService.registerCompany(jwtInfo.peppolId());
        if (registrationResponse.errorCode() == null) {
            if (jwtInfo.peppolActive() == registrationResponse.peppolActive()) {
                if (registrationResponse.peppolActive()) {
                    return ResponseEntity.noContent().build();
                }
                return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body("Access Point registration failed; state unchanged.");
            }
            String token = jwtService.generateToken(
                    jwtInfo.accountType(),
                    jwtInfo.peppolId(),
                    registrationResponse.peppolActive(),
                    jwtInfo.uid()
            );
            return ResponseEntity.ok(token);
        }
        return (ResponseEntity<?>) switch (registrationResponse.errorCode()) {
            case KycErrorCodes.PROXY_REGISTRATION_FAILED,
                 KycErrorCodes.PROXY_UNREGISTRATION_FAILED,
                 KycErrorCodes.PROXY_FAILED,
                 KycErrorCodes.PROXY_REGISTRATION_INTERNAL_ERROR -> ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body("Access Point registration failed; state unchanged.");
            case KycErrorCodes.PROXY_REGISTRATION_CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).body(registrationResponse.body());
            case KycErrorCodes.PROXY_REGISTRATION_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "3600");
            case KycErrorCodes.PROXY_REGISTRATION_SUSPENDED -> ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account is suspended, please contact support@letspeppol.org");
            case KycErrorCodes.PROXY_REGISTRATION_NOT_NEEDED -> ResponseEntity.noContent().build();
            default -> throw new IllegalStateException("Unexpected internal KYC error code: " + registrationResponse.errorCode());
        };
    }

    /// Unregisters (not deleting) peppolId from the Peppol Directory, must call Proxy to unregister from AP
    @PostMapping("/peppol/unregister")
    public ResponseEntity<?> unregister(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        boolean peppolActive = companyService.unregisterCompany(jwtInfo.peppolId());
        if (jwtInfo.peppolActive() == peppolActive) {
            if (!peppolActive) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body("Access Point unregistration failed; state unchanged.");
        }
        String token = jwtService.generateToken(
                jwtInfo.accountType(),
                jwtInfo.peppolId(),
                peppolActive,
                jwtInfo.uid()
        );
        return ResponseEntity.ok(token);
    }

    /// Download signed contract saved for peppolId
    @GetMapping("signed-contract")
    public ResponseEntity<?> signedContract(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        String peppolId = jwtInfo.peppolId();
        UUID externalId = jwtInfo.uid();
        Account account = accountService.getAdminByExternalId(externalId);
        byte[] data = signingService.getContract(peppolId, account.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract_signed.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
