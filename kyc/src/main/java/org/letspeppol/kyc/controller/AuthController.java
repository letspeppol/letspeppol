package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.AuthRequest;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.OwnershipService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping()
@RequiredArgsConstructor
@Tag(name = "KYC Authentication", description = "Authentication endpoints for logging in and switching between linked identities or access contexts.")
public class AuthController {

    private final AccountService accountService;
    private final JwtService jwtService;
    private final OwnershipService ownershipService;
    private final Counter authenticationCounterSuccess;
    private final Counter authenticationCounterFailure;

    /// Generates JWT token on login
    @PostMapping("/api/jwt/auth")
    @Operation(summary = "Authenticate with Basic credentials", description = "Validates email or external identifier credentials and returns a JWT for subsequent authenticated requests.")
    public ResponseEntity<String> auth(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @RequestBody(required = false) AuthRequest request) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            authenticationCounterFailure.increment();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }

        String base64Credentials = authHeader.substring("Basic ".length()).trim();
        byte[] credDecoded = Base64.getDecoder().decode(base64Credentials.getBytes(StandardCharsets.UTF_8));
        String credentials = new String(credDecoded, StandardCharsets.UTF_8);

        final String[] values = credentials.split(":", 2);
        if (values.length != 2) {
            authenticationCounterFailure.increment();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Basic authentication format");
        }
        String emailOrUuid = values[0];
        String password = values[1];
        Account account = accountService.findAccountWithCredentials(emailOrUuid, password); //TODO : Check if account is verified ! Else send info for resend activation mail
        String token = ownershipService.generateAuthToken(account, request);
        authenticationCounterSuccess.increment();

        return ResponseEntity.ok(token);
    }

    /// Generates JWT token on swap
    @PostMapping("/sapi/jwt/swap")
    @Operation(summary = "Swap authentication context", description = "Generates a new JWT for a different linked account or service context available to the current user.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<String> swap(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @RequestBody AuthRequest request) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        String token = ownershipService.generateSwapToken(jwtInfo.uid(), request);
        authenticationCounterSuccess.increment();

        return ResponseEntity.ok(token);
    }

}
