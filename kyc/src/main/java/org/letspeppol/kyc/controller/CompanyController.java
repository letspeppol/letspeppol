package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.AccountInfo;
import org.letspeppol.kyc.dto.CompanySearchResponse;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.exception.ForbiddenException;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.mapper.AccountMapper;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.service.*;
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
@Tag(name = "KYC Company Management", description = "Authenticated company endpoints for loading account context, searching companies, and activating or deactivating Peppol registration.")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final AccountService accountService;
    private final OwnershipService ownershipService;
    private final CompanyService companyService;
    private final JwtService jwtService;
    private final SigningService signingService;

    /// Retrieves account info based on valid JWT token, used by App when peppolId is unknown on getCompany (called by UI right after obtaining JWT token)
    @GetMapping
    @Operation(summary = "Load admin company account", description = "Returns the primary admin account and company details for the authenticated Peppol identifier.")
    public ResponseEntity<AccountInfo> getAdminAccountForToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        Account account = ownershipService.getByPeppolIdAndType(jwtInfo.peppolId(), AccountType.ADMIN).getAccount();
        Company company = companyService.getByPeppolId(jwtInfo.peppolId());
        return ResponseEntity.ok(AccountMapper.toAccountInfo(account, company)); //This will be the ADMIN account and thus the one who signed the contract
    }

    /// Retrieves account info based on valid JWT token, used by App when peppolId is unknown on getCompany (called by UI right after obtaining JWT token)
    @GetMapping("/account")
    @Operation(summary = "Load current account details", description = "Returns the currently authenticated user account together with the related company information.")
    public ResponseEntity<AccountInfo> getAccountForToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        Account account = accountService.getByExternalId(jwtInfo.uid());
        Company company = companyService.getByPeppolId(jwtInfo.peppolId());
        return ResponseEntity.ok(AccountMapper.toAccountInfo(account, company));
    }

    @GetMapping("/search")
    @Operation(summary = "Search companies", description = "Searches company records by VAT number, Peppol identifier, or company name for onboarding and administrative workflows.")
    public ResponseEntity<List<CompanySearchResponse>> search(
            @RequestParam(value = "vatNumber", required = false) String vatNumber,
            @RequestParam(value = "peppolId", required = false) String peppolId,
            @RequestParam(value = "companyName", required = false) String companyName) {
        return ResponseEntity.ok(companyService.search(vatNumber, peppolId, companyName)); //TODO : not really using the JWT, do we need to validate ? Also no comment :-o
    }

    /// Registers peppolId on the Peppol Directory, must call Proxy to register on AP
    @PostMapping("/peppol/register")
    @Operation(summary = "Activate Peppol registration", description = "Registers the authenticated company on the access point and updates the Peppol activation state reflected in the JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated JWT token with refreshed activation state", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "204", description = "Company already active; no response body"),
            @ApiResponse(responseCode = "403", description = "Registration suspended", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "409", description = "Registration conflict with existing provider", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "424", description = "Access point registration failed", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "503", description = "Registration temporarily unavailable")
    })
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
    @Operation(summary = "Deactivate Peppol registration", description = "Unregisters the authenticated company from the access point while keeping the company account itself intact.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated JWT token with refreshed activation state", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "204", description = "Company already inactive; no response body"),
            @ApiResponse(responseCode = "424", description = "Access point unregistration failed", content = @Content(schema = @Schema(implementation = String.class)))
    })
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
    @Operation(summary = "Download signed contract", description = "Returns the signed onboarding contract PDF stored for the authenticated company.")
    public ResponseEntity<?> signedContract(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        String peppolId = jwtInfo.peppolId();
        UUID externalId = jwtInfo.uid();
        Account account = accountService.getByExternalId(externalId);
        byte[] data = signingService.getContract(peppolId, account.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract_signed.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
